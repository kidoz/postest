package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.MAX_WEBSOCKET_BINARY_BYTES
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.RequestFailureException
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketMessage
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.truncateWebSocketMessage
import su.kidoz.postest.domain.port.WebSocketConnection
import su.kidoz.postest.domain.port.WebSocketEvent
import su.kidoz.postest.domain.usecase.ConnectWebSocketUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.WorkspaceCommand
import su.kidoz.postest.viewmodel.WorkspaceStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Owns live WebSocket connections; the workspace owns their visible state. */
class WebSocketManager(
    private val connectWebSocketUseCase: ConnectWebSocketUseCase,
    private val workspace: WorkspaceStore,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
    private val maxReconnectAttempts: Int = 10,
    private val reconnectDelayMillis: (attempt: Int) -> Long = { attempt ->
        minOf(30_000L, 1_000L shl (attempt - 1).coerceIn(0, 5))
    },
) {
    private data class Active(
        val connectionId: String,
        val job: Job,
        val connection: WebSocketConnection?,
    )

    private data class Session(
        val request: HttpRequest,
        val context: ExecutionContext,
    )

    private val active = ConcurrentHashMap<String, Active>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val reconnectJobs = ConcurrentHashMap<String, Job>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()

    fun connect(
        tabId: String,
        request: HttpRequest,
        context: ExecutionContext,
    ) {
        val state = workspace.state.value
        if (state.isClosing || state.tabs.none { it.id == tabId }) return
        val status =
            state.tabs
                .first { it.id == tabId }
                .webSocket
                ?.status
        if (status == WebSocketStatus.CONNECTING || status == WebSocketStatus.CONNECTED) return

        reconnectJobs.remove(tabId)?.cancel()
        sessions[tabId] = Session(request, context)
        val connectionId = UUID.randomUUID().toString()
        workspace.dispatch(WorkspaceCommand.WebSocketConnecting(tabId, connectionId))

        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                var reconnect = false
                try {
                    connectWebSocketUseCase.connect(request, context).fold(
                        onSuccess = { connection ->
                            coroutineContext.ensureActive()
                            if (!claim(tabId, connectionId, connection)) {
                                connection.abort()
                                return@launch
                            }
                            workspace.dispatch(WorkspaceCommand.WebSocketConnected(tabId, connectionId))
                            if (reconnectAttempts.remove(tabId) != null) {
                                addSystemMessage(tabId, "Reconnected")
                            }
                            collect(tabId, connectionId, connection)
                            reconnect = true
                        },
                        onFailure = { error ->
                            val message = publicMessage(error)
                            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, error = message))
                            effects.emit(AppSideEffect.ShowError(message))
                            reconnect = true
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, error = "Connection failed"))
                    effects.emit(AppSideEffect.ShowError("Connection failed"))
                    reconnect = true
                } finally {
                    active.computeIfPresent(tabId) { _, current -> if (current.connectionId == connectionId) null else current }
                }
                if (reconnect) scheduleReconnect(tabId)
            }
        active[tabId] = Active(connectionId, job, connection = null)
        job.start()
    }

    /** Attaches the connection only when the tab still expects this attempt. */
    private fun claim(
        tabId: String,
        connectionId: String,
        connection: WebSocketConnection,
    ): Boolean {
        var claimed = false
        active.computeIfPresent(tabId) { _, current ->
            if (current.connectionId == connectionId) {
                claimed = true
                current.copy(connection = connection)
            } else {
                current
            }
        }
        return claimed
    }

    private suspend fun collect(
        tabId: String,
        connectionId: String,
        connection: WebSocketConnection,
    ) {
        try {
            connection.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Text -> {
                        val (content, truncated) = truncateWebSocketMessage(event.content)
                        workspace.dispatch(
                            WorkspaceCommand.WebSocketMessageAdded(
                                tabId,
                                connectionId,
                                WebSocketMessage(
                                    direction = WebSocketDirection.RECEIVED,
                                    content = content,
                                    truncated = truncated,
                                ),
                            ),
                        )
                    }

                    is WebSocketEvent.Binary -> {
                        workspace.dispatch(
                            WorkspaceCommand.WebSocketMessageAdded(
                                tabId,
                                connectionId,
                                WebSocketMessage(
                                    direction = WebSocketDirection.SYSTEM,
                                    content = "Binary message received (${event.size} bytes)",
                                ),
                            ),
                        )
                    }

                    is WebSocketEvent.Closed -> {
                        workspace.dispatch(
                            WorkspaceCommand.WebSocketClosed(
                                tabId,
                                connectionId,
                                systemMessage = event.reason?.takeIf { it.isNotBlank() } ?: "Connection closed",
                            ),
                        )
                    }
                }
            }
            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, systemMessage = "Connection closed"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, systemMessage = "Connection closed"))
        } finally {
            // Releases transport resources, including a dedicated pinned TLS client.
            connection.abort()
        }
    }

    fun sendMessage(
        tabId: String,
        text: String,
    ) {
        val entry = active[tabId]
        val connection = entry?.connection
        if (entry == null || connection == null) {
            effects.emit(AppSideEffect.ShowError("WebSocket is not connected"))
            return
        }
        val (content, truncated) = truncateWebSocketMessage(text)
        workspace.dispatch(
            WorkspaceCommand.WebSocketMessageAdded(
                tabId,
                entry.connectionId,
                WebSocketMessage(
                    direction = WebSocketDirection.SENT,
                    content = content,
                    truncated = truncated,
                ),
            ),
        )
        scope.launch {
            try {
                connection.send(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effects.emit(AppSideEffect.ShowError("Message could not be sent"))
                workspace.dispatch(
                    WorkspaceCommand.WebSocketMessageAdded(
                        tabId,
                        entry.connectionId,
                        WebSocketMessage(
                            direction = WebSocketDirection.SYSTEM,
                            content = "Message could not be sent",
                        ),
                    ),
                )
            }
        }
    }

    fun sendBinary(
        tabId: String,
        bytes: ByteArray,
    ) {
        if (bytes.isEmpty()) {
            effects.emit(AppSideEffect.ShowError("Binary message is empty"))
            return
        }
        if (bytes.size > MAX_WEBSOCKET_BINARY_BYTES) {
            effects.emit(AppSideEffect.ShowError("Binary message is too large"))
            return
        }
        val entry = active[tabId]
        val connection = entry?.connection
        if (entry == null || connection == null) {
            effects.emit(AppSideEffect.ShowError("WebSocket is not connected"))
            return
        }
        scope.launch {
            try {
                connection.sendBinary(bytes)
                workspace.dispatch(
                    WorkspaceCommand.WebSocketMessageAdded(
                        tabId,
                        entry.connectionId,
                        WebSocketMessage(
                            direction = WebSocketDirection.SYSTEM,
                            content = "Binary message sent (${bytes.size} bytes)",
                        ),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effects.emit(AppSideEffect.ShowError("Binary message could not be sent"))
            }
        }
    }

    fun setAutoReconnect(
        tabId: String,
        enabled: Boolean,
    ) {
        workspace.dispatch(WorkspaceCommand.WebSocketAutoReconnectChanged(tabId, enabled))
        if (enabled) {
            if (workspace.state.value.tabs
                    .firstOrNull { it.id == tabId }
                    ?.webSocket
                    ?.status == WebSocketStatus.DISCONNECTED &&
                sessions.containsKey(tabId)
            ) {
                scheduleReconnect(tabId, immediate = true)
            }
        } else {
            reconnectJobs.remove(tabId)?.cancel()
            reconnectAttempts.remove(tabId)
            addSystemMessage(tabId, "Automatic reconnection disabled")
        }
    }

    fun disconnect(tabId: String) {
        reconnectJobs.remove(tabId)?.cancel()
        reconnectAttempts.remove(tabId)
        sessions.remove(tabId)
        val entry = active.remove(tabId)
        if (entry == null) {
            addSystemMessage(tabId, "Disconnected")
            return
        }
        workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, entry.connectionId, systemMessage = "Disconnected"))
        entry.job.cancel()
        entry.connection?.let { connection ->
            scope.launch { runCatching { connection.close() } }
        }
    }

    fun clearMessages(tabId: String) {
        workspace.dispatch(WorkspaceCommand.WebSocketMessagesCleared(tabId))
    }

    /** Closes connections whose tabs were closed or switched back to HTTP. */
    fun syncTabs(webSocketTabIds: Set<String>) {
        (active.keys + reconnectJobs.keys + sessions.keys)
            .filterNot { it in webSocketTabIds }
            .forEach { tabId ->
                reconnectJobs.remove(tabId)?.cancel()
                reconnectAttempts.remove(tabId)
                sessions.remove(tabId)
                val entry = active.remove(tabId) ?: return@forEach
                workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, entry.connectionId, systemMessage = "Disconnected"))
                entry.job.cancel()
                entry.connection?.let { connection ->
                    scope.launch { runCatching { connection.close() } }
                }
            }
    }

    @Suppress("ReturnCount")
    private fun scheduleReconnect(
        tabId: String,
        immediate: Boolean = false,
    ) {
        val state = workspace.state.value
        if (state.isClosing) return
        val tab = state.tabs.firstOrNull { it.id == tabId } ?: return
        if (tab.request.protocol != RequestProtocol.WEBSOCKET) return
        if (tab.webSocket?.autoReconnect != true) return
        if (reconnectJobs.containsKey(tabId) || active.containsKey(tabId)) return
        val session = sessions[tabId] ?: return

        val attempt = (reconnectAttempts[tabId] ?: 0) + 1
        if (attempt > maxReconnectAttempts) {
            reconnectAttempts.remove(tabId)
            addSystemMessage(tabId, "Reconnection stopped after $maxReconnectAttempts attempts")
            return
        }
        reconnectAttempts[tabId] = attempt

        val delayMillis = if (immediate) 0L else reconnectDelayMillis(attempt)
        addSystemMessage(
            tabId,
            if (delayMillis > 0) {
                "Reconnecting in ${(delayMillis / 1_000).coerceAtLeast(1)}s (attempt $attempt/$maxReconnectAttempts)"
            } else {
                "Reconnecting (attempt $attempt/$maxReconnectAttempts)"
            },
        )
        val job =
            scope.launch {
                try {
                    if (delayMillis > 0) delay(delayMillis)
                    val current =
                        workspace.state.value.tabs
                            .firstOrNull { it.id == tabId } ?: return@launch
                    if (current.request.protocol != RequestProtocol.WEBSOCKET || current.webSocket?.autoReconnect != true) {
                        return@launch
                    }
                    // Remove before connecting so connect() cannot cancel this job when it clears pending retries.
                    reconnectJobs.remove(tabId)
                    connect(tabId, session.request, session.context)
                } finally {
                    reconnectJobs.remove(tabId)
                }
            }
        reconnectJobs[tabId] = job
    }

    private fun addSystemMessage(
        tabId: String,
        content: String,
    ) {
        workspace.dispatch(
            WorkspaceCommand.WebSocketSystemMessage(
                tabId,
                WebSocketMessage(direction = WebSocketDirection.SYSTEM, content = content),
            ),
        )
    }

    suspend fun quiesce() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        sessions.clear()
        reconnectAttempts.clear()
        val entries = active.entries.toList()
        active.clear()
        entries.forEach { (tabId, entry) ->
            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, entry.connectionId, systemMessage = "Disconnected"))
            entry.job.cancel()
        }
        entries.forEach { (_, entry) ->
            entry.connection?.let { connection ->
                withContext(NonCancellable) { runCatching { connection.close() } }
            }
        }
        entries.forEach { (_, entry) -> entry.job.join() }
    }

    fun cleanup() {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        sessions.clear()
        reconnectAttempts.clear()
        active.values.forEach { entry ->
            entry.connection?.abort()
            entry.job.cancel()
        }
        active.clear()
    }

    private fun publicMessage(error: Throwable): String =
        when (error) {
            is RequestFailureException -> error.reason.description
            is OAuth2Exception -> error.failure.description
            else -> "Connection failed"
        }
}
