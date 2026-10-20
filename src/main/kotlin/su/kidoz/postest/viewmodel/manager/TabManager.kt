package su.kidoz.postest.viewmodel.manager

import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.viewmodel.TabState
import su.kidoz.postest.viewmodel.WorkspaceCommand
import su.kidoz.postest.viewmodel.WorkspaceStore

/** Tab commands are routed through the workspace owner; this facade has no writable state. */
class TabManager(
    private val workspace: WorkspaceStore,
) {
    fun newTab(): String = open(TabState())

    fun openRequest(
        request: HttpRequest,
        collectionItemId: String? = null,
    ): String = open(TabState(request = request, collectionItemId = collectionItemId, webSocket = initialWebSocket(request)))

    fun openHistoryEntry(entry: HistoryEntry): String =
        open(TabState(request = entry.request, response = entry.response, webSocket = initialWebSocket(entry.request)))

    private fun initialWebSocket(request: HttpRequest): WebSocketTabState? =
        if (request.protocol == RequestProtocol.WEBSOCKET) WebSocketTabState() else null

    private fun open(tab: TabState): String {
        workspace.dispatch(WorkspaceCommand.OpenTab(tab))
        return tab.id
    }

    fun updateRequest(
        tabId: String,
        request: HttpRequest,
    ) = workspace.dispatch(WorkspaceCommand.EditRequest(tabId, request))

    fun selectTab(tabId: String) = workspace.dispatch(WorkspaceCommand.SelectTab(tabId))

    fun closeTab(tabId: String) = close(setOf(tabId))

    fun closeAllTabs() =
        close(
            workspace.state.value.tabs
                .map { it.id }
                .toSet(),
        )

    fun closeOtherTabs(tabId: String) =
        close(
            workspace.state.value.tabs
                .filter { it.id != tabId }
                .map { it.id }
                .toSet(),
        )

    fun closeTabsToLeft(tabId: String) {
        val tabs = workspace.state.value.tabs
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index > 0) close(tabs.take(index).map { it.id }.toSet())
    }

    fun closeTabsToRight(tabId: String) {
        val tabs = workspace.state.value.tabs
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) close(tabs.drop(index + 1).map { it.id }.toSet())
    }

    private fun close(ids: Set<String>) = workspace.dispatch(WorkspaceCommand.CloseTabs(ids))

    fun discardPendingTabs() = workspace.dispatch(WorkspaceCommand.DiscardPending())

    fun cancelTabClose() = workspace.dispatch(WorkspaceCommand.CancelClose)

    fun getActiveTab(): TabState? = workspace.state.value.activeTab

    fun getActiveTabId(): String? = workspace.state.value.activeTabId
}
