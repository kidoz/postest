package su.kidoz.postest.viewmodel.manager

import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.StateUpdater
import su.kidoz.postest.viewmodel.TabState

/**
 * Manager for tab lifecycle and request state within tabs.
 * Handles: tab creation, closing, selection, request updates, loading state.
 */
class TabManager(
    private val stateUpdater: StateUpdater<AppState>,
    private val stateProvider: () -> AppState,
) {
    /**
     * Creates a new tab with default empty request.
     * @return ID of the newly created tab
     */
    fun newTab(): String {
        val newTab = TabState()
        stateUpdater.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
        return newTab.id
    }

    /**
     * Asks before discarding edits. Clean tabs close immediately.
     */
    fun closeTab(tabId: String) {
        closeTabs(listOf(tabId))
    }

    fun closeAllTabs() {
        closeTabs(stateProvider().tabs.map { it.id })
    }

    fun closeOtherTabs(tabId: String) {
        closeTabs(stateProvider().tabs.filter { it.id != tabId }.map { it.id })
    }

    fun closeTabsToLeft(tabId: String) {
        val tabs = stateProvider().tabs
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index > 0) closeTabs(tabs.take(index).map { it.id })
    }

    fun closeTabsToRight(tabId: String) {
        val tabs = stateProvider().tabs
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0 && index < tabs.lastIndex) closeTabs(tabs.drop(index + 1).map { it.id })
    }

    private fun closeTabs(tabIds: List<String>) {
        stateUpdater.update { state ->
            val requestedIds = tabIds.toSet()
            val affected = state.tabs.filter { it.id in requestedIds }
            when {
                affected.isEmpty() -> state
                affected.any { it.isDirty } -> state.copy(pendingCloseTabIds = affected.map { it.id })
                else -> removeTabs(state, requestedIds)
            }
        }
    }

    fun discardPendingTabs() {
        stateUpdater.update { state ->
            if (state.pendingCloseTabIds.isEmpty()) {
                state
            } else {
                removeTabs(state, state.pendingCloseTabIds.toSet())
            }
        }
    }

    fun cancelTabClose() {
        stateUpdater.update { it.copy(pendingCloseTabIds = emptyList()) }
    }

    private fun removeTabs(
        state: AppState,
        tabIds: Set<String>,
    ): AppState {
        if (tabIds.isEmpty()) return state
        val remaining = state.tabs.filterNot { it.id in tabIds }.ifEmpty { listOf(TabState()) }
        val currentActiveId = state.activeTab?.id
        val activeId =
            if (currentActiveId != null && currentActiveId in tabIds) {
                val firstRemovedIndex = state.tabs.indexOfFirst { it.id in tabIds }
                val keptBefore = state.tabs.take(firstRemovedIndex.coerceAtLeast(0)).count { it.id !in tabIds }
                remaining[keptBefore.coerceAtMost(remaining.lastIndex)].id
            } else {
                state.activeTabId
            }
        return state.copy(tabs = remaining, activeTabId = activeId, pendingCloseTabIds = emptyList())
    }

    /**
     * Selects a tab by ID.
     */
    fun selectTab(tabId: String) {
        stateUpdater.update { state ->
            state.copy(activeTabId = tabId)
        }
    }

    /**
     * Updates the request in a specific tab.
     */
    fun updateRequest(
        tabId: String,
        request: HttpRequest,
    ) {
        stateUpdater.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) {
                            val previousBody = tab.request.body ?: RequestBody.None
                            val nextBody = request.body ?: RequestBody.None
                            val drafts = tab.bodyDrafts + (previousBody::class to previousBody)
                            val restoredBody =
                                if (previousBody::class != nextBody::class) {
                                    drafts[nextBody::class] ?: nextBody
                                } else {
                                    nextBody
                                }
                            tab.copy(
                                request = request.copy(body = if (request.body == null) null else restoredBody),
                                isDirty = tab.isDirty || request != tab.request,
                                bodyDrafts = drafts + (restoredBody::class to restoredBody),
                            )
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    /**
     * Sets the loading state of a tab.
     */
    fun setTabLoading(
        tabId: String,
        isLoading: Boolean,
        error: String? = null,
    ) {
        stateUpdater.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) {
                            tab.copy(isLoading = isLoading, error = error)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    /**
     * Sets the response for a tab.
     */
    fun setTabResponse(
        tabId: String,
        response: HttpResponse?,
        error: String?,
    ) {
        stateUpdater.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) {
                            tab.copy(response = response, isLoading = false, error = error)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    /**
     * Opens a request in a new tab.
     * @param collectionItemId the collection item the request was opened from, if any
     * @return ID of the newly created tab
     */
    fun openRequest(
        request: HttpRequest,
        collectionItemId: String? = null,
    ): String {
        val newTab = TabState(request = request, collectionItemId = collectionItemId)
        stateUpdater.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
        return newTab.id
    }

    /**
     * Opens a history entry in a new tab (with request and response).
     * @return ID of the newly created tab
     */
    fun openHistoryEntry(entry: HistoryEntry): String {
        val newTab =
            TabState(
                request = entry.request,
                response = entry.response,
            )
        stateUpdater.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
        return newTab.id
    }

    /**
     * Gets the currently active tab.
     */
    fun getActiveTab(): TabState? {
        val state = stateProvider()
        return state.tabs.find { it.id == state.activeTabId } ?: state.tabs.firstOrNull()
    }

    /**
     * Gets the active tab ID.
     */
    fun getActiveTabId(): String? = stateProvider().activeTabId
}
