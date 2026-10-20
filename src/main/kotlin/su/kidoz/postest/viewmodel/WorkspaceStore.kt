package su.kidoz.postest.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.MAX_WEBSOCKET_MESSAGES
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketMessage
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.domain.model.limitBodyForStorage

/** Values with shared invariants are changed in one atomic workspace transition. */
data class WorkspaceState(
    val tabs: List<TabState> = listOf(TabState()),
    val activeTabId: String? = tabs.firstOrNull()?.id,
    val pendingCloseTabIds: List<String> = emptyList(),
    val isRestored: Boolean = false,
    val isClosing: Boolean = false,
) {
    val activeTab: TabState? get() = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()
}

sealed interface WorkspaceCommand {
    data class OpenTab(
        val tab: TabState,
    ) : WorkspaceCommand

    data class EditRequest(
        val tabId: String,
        val request: HttpRequest,
    ) : WorkspaceCommand

    data class SelectTab(
        val tabId: String,
    ) : WorkspaceCommand

    data class CloseTabs(
        val tabIds: Set<String>,
        val replacement: TabState = TabState(),
    ) : WorkspaceCommand

    data class DiscardPending(
        val replacement: TabState = TabState(),
    ) : WorkspaceCommand

    data object CancelClose : WorkspaceCommand

    data class RequestStarted(
        val tabId: String,
        val executionId: String,
    ) : WorkspaceCommand

    data class RequestFinished(
        val tabId: String,
        val executionId: String,
        val response: HttpResponse?,
        val error: String?,
    ) : WorkspaceCommand

    data class RequestCancelled(
        val tabId: String,
        val executionId: String,
    ) : WorkspaceCommand

    data class WebSocketConnecting(
        val tabId: String,
        val connectionId: String,
    ) : WorkspaceCommand

    data class WebSocketConnected(
        val tabId: String,
        val connectionId: String,
    ) : WorkspaceCommand

    data class WebSocketMessageAdded(
        val tabId: String,
        val connectionId: String,
        val message: WebSocketMessage,
    ) : WorkspaceCommand

    data class WebSocketClosed(
        val tabId: String,
        val connectionId: String,
        val error: String? = null,
        val systemMessage: String? = null,
    ) : WorkspaceCommand

    data class WebSocketMessagesCleared(
        val tabId: String,
    ) : WorkspaceCommand

    data class Restore(
        val session: DraftSession?,
        val initial: DraftSession,
    ) : WorkspaceCommand

    data object BeginClosing : WorkspaceCommand

    data object Resume : WorkspaceCommand
}

/** The sole mutable owner of open tabs. Commands contain IDs allocated outside update/reducer lambdas. */
class WorkspaceStore(
    initial: WorkspaceState = WorkspaceState(),
) {
    private val mutableState = MutableStateFlow(initial)
    val state = mutableState.asStateFlow()

    fun dispatch(command: WorkspaceCommand) {
        mutableState.update { current ->
            if (current.isClosing && command !is WorkspaceCommand.Resume && command !is WorkspaceCommand.Restore) {
                current
            } else {
                reduce(current, command)
            }
        }
    }

    private fun reduce(
        current: WorkspaceState,
        command: WorkspaceCommand,
    ): WorkspaceState =
        when (command) {
            is WorkspaceCommand.OpenTab -> {
                current.copy(tabs = current.tabs + command.tab, activeTabId = command.tab.id)
            }

            is WorkspaceCommand.EditRequest -> {
                current.mapTab(command.tabId) { tab ->
                    val previous = tab.request.body ?: RequestBody.None
                    val next = command.request.body ?: RequestBody.None
                    val drafts = tab.bodyDrafts + (previous::class to previous)
                    val restored = if (previous::class != next::class) drafts[next::class] ?: next else next
                    tab.copy(
                        request = command.request.copy(body = if (command.request.body == null) null else restored),
                        isDirty = tab.isDirty || command.request != tab.request,
                        bodyDrafts = drafts + (restored::class to restored),
                        webSocket =
                            if (command.request.protocol == RequestProtocol.WEBSOCKET) {
                                tab.webSocket ?: WebSocketTabState()
                            } else {
                                null
                            },
                    )
                }
            }

            is WorkspaceCommand.SelectTab -> {
                if (current.tabs.any { it.id == command.tabId }) current.copy(activeTabId = command.tabId) else current
            }

            is WorkspaceCommand.CloseTabs -> {
                val affected = current.tabs.filter { it.id in command.tabIds }
                if (affected.any { it.isDirty }) {
                    current.copy(pendingCloseTabIds = affected.map { it.id })
                } else {
                    removeTabs(current, command.tabIds, command.replacement)
                }
            }

            is WorkspaceCommand.DiscardPending -> {
                removeTabs(current, current.pendingCloseTabIds.toSet(), command.replacement)
            }

            WorkspaceCommand.CancelClose -> {
                current.copy(pendingCloseTabIds = emptyList())
            }

            is WorkspaceCommand.RequestStarted -> {
                current.mapTab(command.tabId) {
                    it.copy(executionId = command.executionId, isLoading = true, error = null)
                }
            }

            is WorkspaceCommand.RequestFinished -> {
                current.mapTab(command.tabId) {
                    if (it.executionId != command.executionId) {
                        it
                    } else {
                        it.copy(response = command.response, error = command.error, isLoading = false, executionId = null)
                    }
                }
            }

            is WorkspaceCommand.RequestCancelled -> {
                current.mapTab(command.tabId) {
                    if (it.executionId != command.executionId) {
                        it
                    } else {
                        it.copy(isLoading = false, error = "Request cancelled", executionId = null)
                    }
                }
            }

            is WorkspaceCommand.WebSocketConnecting -> {
                current.mapTab(command.tabId) { tab ->
                    tab.withWebSocket { it.copy(status = WebSocketStatus.CONNECTING, connectionId = command.connectionId, error = null) }
                }
            }

            is WorkspaceCommand.WebSocketConnected -> {
                current.mapTab(command.tabId) { tab ->
                    tab.withWebSocket(command.connectionId) { it.copy(status = WebSocketStatus.CONNECTED, error = null) }
                }
            }

            is WorkspaceCommand.WebSocketMessageAdded -> {
                current.mapTab(command.tabId) { tab ->
                    tab.withWebSocket(command.connectionId) { state ->
                        state.copy(messages = state.messages.append(command.message))
                    }
                }
            }

            is WorkspaceCommand.WebSocketClosed -> {
                current.mapTab(command.tabId) { tab ->
                    tab.withWebSocket(command.connectionId) { state ->
                        val messages =
                            command.systemMessage
                                ?.let { state.messages.append(WebSocketMessage(direction = WebSocketDirection.SYSTEM, content = it)) }
                                ?: state.messages
                        state.copy(status = WebSocketStatus.DISCONNECTED, connectionId = null, error = command.error, messages = messages)
                    }
                }
            }

            is WorkspaceCommand.WebSocketMessagesCleared -> {
                current.mapTab(command.tabId) { tab -> tab.withWebSocket { it.copy(messages = emptyList()) } }
            }

            is WorkspaceCommand.Restore -> {
                restore(current, command)
            }

            WorkspaceCommand.BeginClosing -> {
                current.copy(
                    isClosing = true,
                    tabs = current.tabs.map { it.copy(isLoading = false, executionId = null) },
                )
            }

            WorkspaceCommand.Resume -> {
                current.copy(isClosing = false)
            }
        }

    private fun restore(
        current: WorkspaceState,
        command: WorkspaceCommand.Restore,
    ): WorkspaceState {
        if (current.isRestored) return current
        val saved = command.session ?: return current.copy(isRestored = true)
        val restored =
            saved.tabs.map { tab ->
                TabState(
                    id = tab.id,
                    request = tab.request,
                    collectionItemId = tab.collectionItemId,
                    response = tab.response,
                    error = tab.error,
                    isDirty = tab.isDirty,
                    bodyDrafts = tab.bodyDrafts.associateBy { it::class },
                    webSocket = if (tab.request.protocol == RequestProtocol.WEBSOCKET) WebSocketTabState() else null,
                )
            }
        // Preserve edits accepted while the disk read was pending. Current values win on ID collisions.
        val unchanged = current.toDraftSession() == command.initial
        val liveIds = current.tabs.map { it.id }.toSet()
        val tabs = if (unchanged) restored else restored.filterNot { it.id in liveIds } + current.tabs
        if (tabs.isEmpty()) return current.copy(isRestored = true)
        val selection = if (unchanged) saved.activeTabId else current.activeTabId
        return current.copy(
            tabs = tabs,
            activeTabId = selection?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
            isRestored = true,
        )
    }

    private fun removeTabs(
        current: WorkspaceState,
        ids: Set<String>,
        replacement: TabState,
    ): WorkspaceState {
        if (ids.isEmpty()) return current
        val remaining = current.tabs.filterNot { it.id in ids }.ifEmpty { listOf(replacement) }
        val active =
            if (current.activeTab?.id in ids) {
                val firstRemovedIndex = current.tabs.indexOfFirst { it.id in ids }.coerceAtLeast(0)
                val keptBefore = current.tabs.take(firstRemovedIndex).count { it.id !in ids }
                remaining[keptBefore.coerceAtMost(remaining.lastIndex)].id
            } else {
                current.activeTabId
            }
        return current.copy(tabs = remaining, activeTabId = active, pendingCloseTabIds = emptyList())
    }

    private fun WorkspaceState.mapTab(
        id: String,
        transform: (TabState) -> TabState,
    ): WorkspaceState = copy(tabs = tabs.map { if (it.id == id) transform(it) else it })

    private fun TabState.withWebSocket(transform: (WebSocketTabState) -> WebSocketTabState): TabState =
        webSocket?.let { copy(webSocket = transform(it)) } ?: this

    private fun TabState.withWebSocket(
        connectionId: String,
        transform: (WebSocketTabState) -> WebSocketTabState,
    ): TabState = withWebSocket { if (it.connectionId == connectionId) transform(it) else it }

    private fun List<WebSocketMessage>.append(message: WebSocketMessage): List<WebSocketMessage> =
        (this + message).takeLast(MAX_WEBSOCKET_MESSAGES)
}

/** Persistence is a projection of live workspace state, never a second live state owner. */
fun WorkspaceState.toDraftSession(): DraftSession =
    DraftSession(
        tabs =
            tabs.map { tab ->
                val activeType = (tab.request.body ?: RequestBody.None)::class
                DraftTab(
                    id = tab.id,
                    request = tab.request,
                    isDirty = tab.isDirty,
                    bodyDrafts = tab.bodyDrafts.values.filter { it::class != activeType && it !is RequestBody.None },
                    collectionItemId = tab.collectionItemId,
                    response = tab.response?.limitBodyForStorage(),
                    error = tab.error,
                )
            },
        activeTabId = activeTabId,
    )
