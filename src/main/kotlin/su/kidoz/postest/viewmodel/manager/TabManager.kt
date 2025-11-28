package su.kidoz.postest.viewmodel.manager

import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
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
     * Closes a tab. If closing the last tab, creates a new empty one.
     */
    fun closeTab(tabId: String) {
        stateUpdater.update { state ->
            val newTabs = state.tabs.filter { it.id != tabId }
            if (newTabs.isEmpty()) {
                val newTab = TabState()
                state.copy(tabs = listOf(newTab), activeTabId = newTab.id)
            } else {
                val newActiveTabId =
                    if (state.activeTabId == tabId) {
                        newTabs.firstOrNull()?.id
                    } else {
                        state.activeTabId
                    }
                state.copy(tabs = newTabs, activeTabId = newActiveTabId)
            }
        }
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
                            tab.copy(request = request, isDirty = true)
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
     * @return ID of the newly created tab
     */
    fun openRequest(request: HttpRequest): String {
        val newTab = TabState(request = request)
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
