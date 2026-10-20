package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.RequestFailureException
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
) {
    private data class Active(
        val connectionId: String,
        val job: Job,
        val connection: WebSocketConnection?,
    )

    private val active = ConcurrentHashMap<String, Active>()

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

        val connectionId = UUID.randomUUID().toString()
        workspace.dispatch(WorkspaceCommand.WebSocketConnecting(tabId, connectionId))

        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    connectWebSocketUseCase.connect(request, context).fold(
                        onSuccess = { connection ->
                            coroutineContext.ensureActive()
                            if (!claim(tabId, connectionId, connection)) {
                                connection.abort()
                                return@launch
                            }
                            workspace.dispatch(WorkspaceCommand.WebSocketConnected(tabId, connectionId))
                            collect(tabId, connectionId, connection)
                        },
                        onFailure = { error ->
                            val message = publicMessage(error)
                            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, error = message))
                            effects.emit(AppSideEffect.ShowError(message))
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, connectionId, error = "Connection failed"))
                    effects.emit(AppSideEffect.ShowError("Connection failed"))
                } finally {
                    active.computeIfPresent(tabId) { _, current -> if (current.connectionId == connectionId) null else current }
                }
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

    fun disconnect(tabId: String) {
        val entry = active.remove(tabId) ?: return
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
        active.keys.filterNot { it in webSocketTabIds }.forEach { tabId ->
            val entry = active.remove(tabId) ?: return@forEach
            workspace.dispatch(WorkspaceCommand.WebSocketClosed(tabId, entry.connectionId, systemMessage = "Disconnected"))
            entry.job.cancel()
            entry.connection?.let { connection ->
                scope.launch { runCatching { connection.close() } }
            }
        }
    }

    suspend fun quiesce() {
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
