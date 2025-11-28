package su.kidoz.postest.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

// State
data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val request: HttpRequest = HttpRequest(),
    val response: HttpResponse? = null,
    val isLoading: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
)

data class AppState(
    val tabs: List<TabState> = listOf(TabState()),
    val activeTabId: String? = null,
    val collections: List<RequestCollection> = emptyList(),
    val environments: List<Environment> = emptyList(),
    val activeEnvironmentId: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val isDarkTheme: Boolean = false,
    val showEnvironmentDialog: Boolean = false,
    val showNewCollectionDialog: Boolean = false,
    val isInitialized: Boolean = false,
) {
    val activeTab: TabState?
        get() = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull()

    val activeEnvironment: Environment?
        get() = environments.find { it.id == activeEnvironmentId }
}

// Side Effects (one-time events)
sealed class AppSideEffect {
    data class ShowToast(
        val message: String,
    ) : AppSideEffect()

    data class ShowError(
        val message: String,
    ) : AppSideEffect()

    data object NavigateToSettings : AppSideEffect()
}

class MainViewModel(
    private val executeRequestUseCase: ExecuteRequestUseCase,
    private val manageCollectionsUseCase: ManageCollectionsUseCase,
    private val manageEnvironmentsUseCase: ManageEnvironmentsUseCase,
    private val manageHistoryUseCase: ManageHistoryUseCase,
    private val importCollectionUseCase: ImportCollectionUseCase,
    private val exportCollectionUseCase: ExportCollectionUseCase,
) {
    private val supervisorJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(supervisorJob + Dispatchers.Main)

    // Track active request jobs per tab for cancellation
    private val activeRequestJobs = ConcurrentHashMap<String, Job>()

    private val _state =
        MutableStateFlow(
            AppState().let { state ->
                state.copy(activeTabId = state.tabs.firstOrNull()?.id)
            },
        )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _sideEffect = Channel<AppSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            // Subscribe to collections updates
            launch {
                manageCollectionsUseCase.collections.collect { collections ->
                    _state.update { it.copy(collections = collections) }
                }
            }

            // Subscribe to environments updates
            launch {
                manageEnvironmentsUseCase.environments.collect { environments ->
                    _state.update { it.copy(environments = environments) }
                }
            }

            // Subscribe to active environment updates
            launch {
                manageEnvironmentsUseCase.activeEnvironmentId.collect { activeId ->
                    _state.update { it.copy(activeEnvironmentId = activeId) }
                }
            }

            // Subscribe to history updates
            launch {
                manageHistoryUseCase.history.collect { history ->
                    _state.update { it.copy(history = history) }
                }
            }

            // Initial load
            manageCollectionsUseCase.loadCollections()
            manageEnvironmentsUseCase.loadEnvironments()
            manageHistoryUseCase.loadHistory()

            _state.update { it.copy(isInitialized = true) }
        }
    }

    // Intent: Update request in active tab
    fun updateRequest(request: HttpRequest) {
        val activeTabId = _state.value.activeTabId ?: return
        _state.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == activeTabId) {
                            tab.copy(request = request, isDirty = true)
                        } else {
                            tab
                        }
                    },
            )
        }
    }

    // Intent: Send HTTP request
    fun sendRequest() {
        val activeTab = _state.value.activeTab ?: return
        val tabId = activeTab.id

        _state.update { state ->
            state.copy(
                tabs =
                    state.tabs.map { tab ->
                        if (tab.id == tabId) {
                            tab.copy(isLoading = true, error = null)
                        } else {
                            tab
                        }
                    },
            )
        }

        // Atomically cancel old job and store new one to prevent race conditions
        activeRequestJobs.compute(tabId) { _, oldJob ->
            oldJob?.cancel()

            viewModelScope.launch {
                val startTime = System.currentTimeMillis()
                val result = executeRequestUseCase(activeTab.request)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Remove job from tracking when complete
                activeRequestJobs.remove(tabId)

                result.fold(
                    onSuccess = { response ->
                        // Add to history
                        val historyEntry =
                            HistoryEntry(
                                request = activeTab.request,
                                response = response,
                                duration = duration,
                            )
                        manageHistoryUseCase.addHistoryEntry(historyEntry)

                        _state.update { state ->
                            state.copy(
                                tabs =
                                    state.tabs.map { tab ->
                                        if (tab.id == tabId) {
                                            tab.copy(response = response, isLoading = false, error = null)
                                        } else {
                                            tab
                                        }
                                    },
                            )
                        }
                    },
                    onFailure = { error ->
                        logger.error(error) { "HTTP request failed: ${error.message}" }

                        val errorMsg = error.message ?: "Unknown error"

                        // Add to history with error details preserved
                        val historyEntry =
                            HistoryEntry(
                                request = activeTab.request,
                                response = null,
                                errorMessage = errorMsg,
                                duration = duration,
                            )
                        manageHistoryUseCase.addHistoryEntry(historyEntry)

                        _state.update { state ->
                            state.copy(
                                tabs =
                                    state.tabs.map { tab ->
                                        if (tab.id == tabId) {
                                            tab.copy(
                                                response = null,
                                                isLoading = false,
                                                error = errorMsg,
                                            )
                                        } else {
                                            tab
                                        }
                                    },
                            )
                        }

                        _sideEffect.trySend(AppSideEffect.ShowError(errorMsg))
                    },
                )
            }
        }
    }

    // Intent: Cancel current request
    fun cancelRequest() {
        val activeTabId = _state.value.activeTabId ?: return
        activeRequestJobs[activeTabId]?.let { job ->
            job.cancel()
            activeRequestJobs.remove(activeTabId)

            _state.update { state ->
                state.copy(
                    tabs =
                        state.tabs.map { tab ->
                            if (tab.id == activeTabId) {
                                tab.copy(isLoading = false, error = "Request cancelled")
                            } else {
                                tab
                            }
                        },
                )
            }

            _sideEffect.trySend(AppSideEffect.ShowToast("Request cancelled"))
        }
    }

    // Intent: Create new tab
    fun newTab() {
        val newTab = TabState()
        _state.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    // Intent: Close tab
    fun closeTab(tabId: String) {
        _state.update { state ->
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

    // Intent: Select tab
    fun selectTab(tabId: String) {
        _state.update { state ->
            state.copy(activeTabId = tabId)
        }
    }

    // Intent: Open request from collection
    fun openRequest(request: HttpRequest) {
        val newTab = TabState(request = request)
        _state.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    // Intent: Open history entry
    fun openHistoryEntry(entry: HistoryEntry) {
        val newTab =
            TabState(
                request = entry.request,
                response = entry.response,
            )
        _state.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    // Intent: Select environment
    fun selectEnvironment(environmentId: String?) {
        viewModelScope.launch {
            manageEnvironmentsUseCase.setActiveEnvironment(environmentId)
        }
    }

    // Intent: Clear history
    fun clearHistory() {
        viewModelScope.launch {
            manageHistoryUseCase.clearHistory()
            _sideEffect.trySend(AppSideEffect.ShowToast("History cleared"))
        }
    }

    // Intent: Toggle theme
    fun toggleTheme() {
        _state.update { state ->
            state.copy(isDarkTheme = !state.isDarkTheme)
        }
    }

    // Intent: Show environment dialog
    fun showEnvironmentDialog() {
        _state.update { state ->
            state.copy(showEnvironmentDialog = true)
        }
    }

    // Intent: Hide environment dialog
    fun hideEnvironmentDialog() {
        _state.update { state ->
            state.copy(showEnvironmentDialog = false)
        }
    }

    // Intent: Show new collection dialog
    fun showNewCollectionDialog() {
        _state.update { state ->
            state.copy(showNewCollectionDialog = true)
        }
    }

    // Intent: Hide new collection dialog
    fun hideNewCollectionDialog() {
        _state.update { state ->
            state.copy(showNewCollectionDialog = false)
        }
    }

    // Intent: Create collection
    fun createCollection(name: String) {
        viewModelScope.launch {
            manageCollectionsUseCase.createCollection(name)
            _state.update { it.copy(showNewCollectionDialog = false) }
            _sideEffect.trySend(AppSideEffect.ShowToast("Collection '$name' created"))
        }
    }

    // Intent: Add request to collection
    fun addRequestToCollection(
        collection: RequestCollection,
        requestName: String,
    ) {
        viewModelScope.launch {
            val activeTab = _state.value.activeTab ?: return@launch
            val name = requestName.ifBlank { "New Request" }
            manageCollectionsUseCase.addRequestToCollection(
                collectionId = collection.id,
                request = activeTab.request,
                name = name,
            )
            _sideEffect.trySend(AppSideEffect.ShowToast("Request '$name' added to '${collection.name}'"))
        }
    }

    // Intent: Save environment (create if new, update if exists)
    fun saveEnvironment(environment: Environment) {
        viewModelScope.launch {
            val isNew = _state.value.environments.none { it.id == environment.id }
            if (isNew) {
                // New environment - need to insert it
                manageEnvironmentsUseCase.saveEnvironment(environment)
                _sideEffect.trySend(AppSideEffect.ShowToast("Environment '${environment.name}' created"))
            } else {
                manageEnvironmentsUseCase.updateEnvironment(environment)
                _sideEffect.trySend(AppSideEffect.ShowToast("Environment '${environment.name}' saved"))
            }
        }
    }

    // Intent: Create environment
    fun createEnvironment(name: String) {
        viewModelScope.launch {
            manageEnvironmentsUseCase.createEnvironment(name)
            _sideEffect.trySend(AppSideEffect.ShowToast("Environment '$name' created"))
        }
    }

    // Intent: Delete environment
    fun deleteEnvironment(environmentId: String) {
        viewModelScope.launch {
            val envName =
                _state.value.environments
                    .find { it.id == environmentId }
                    ?.name ?: ""
            manageEnvironmentsUseCase.deleteEnvironment(environmentId)
            _sideEffect.trySend(AppSideEffect.ShowToast("Environment '$envName' deleted"))
        }
    }

    // Intent: Delete collection
    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            val collectionName =
                _state.value.collections
                    .find { it.id == collectionId }
                    ?.name ?: ""
            manageCollectionsUseCase.deleteCollection(collectionId)
            _sideEffect.trySend(AppSideEffect.ShowToast("Collection '$collectionName' deleted"))
        }
    }

    // Intent: Rename collection
    fun renameCollection(
        collectionId: String,
        newName: String,
    ) {
        viewModelScope.launch {
            manageCollectionsUseCase.renameCollection(collectionId, newName)
            _sideEffect.trySend(AppSideEffect.ShowToast("Collection renamed to '$newName'"))
        }
    }

    // Intent: Delete collection item (request or folder)
    fun deleteCollectionItem(itemId: String) {
        viewModelScope.launch {
            manageCollectionsUseCase.deleteCollectionItem(itemId)
            _sideEffect.trySend(AppSideEffect.ShowToast("Item deleted"))
        }
    }

    // Intent: Rename collection item (request or folder)
    fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) {
        viewModelScope.launch {
            manageCollectionsUseCase.renameCollectionItem(itemId, newName)
            _sideEffect.trySend(AppSideEffect.ShowToast("Item renamed to '$newName'"))
        }
    }

    // Intent: Import collection from file
    fun importCollection(file: File) {
        viewModelScope.launch {
            logger.info { "Importing collection from file: ${file.absolutePath}" }
            val result = importCollectionUseCase.importFromFile(file)
            result.fold(
                onSuccess = { collection ->
                    _sideEffect.trySend(AppSideEffect.ShowToast("Collection '${collection.name}' imported successfully"))
                },
                onFailure = { error ->
                    logger.error(error) { "Failed to import collection: ${error.message}" }
                    _sideEffect.trySend(AppSideEffect.ShowError("Import failed: ${error.message}"))
                },
            )
        }
    }

    // Intent: Export collection to Postman format
    fun exportCollection(
        collectionId: String,
        file: File,
    ) {
        viewModelScope.launch {
            val collection = _state.value.collections.find { it.id == collectionId }
            if (collection == null) {
                _sideEffect.trySend(AppSideEffect.ShowError("Collection not found"))
                return@launch
            }

            logger.info { "Exporting collection '${collection.name}' to: ${file.absolutePath}" }
            val result = exportCollectionUseCase.exportToPostman(collection, file)
            result.fold(
                onSuccess = {
                    _sideEffect.trySend(AppSideEffect.ShowToast("Collection '${collection.name}' exported successfully"))
                },
                onFailure = { error ->
                    logger.error(error) { "Failed to export collection: ${error.message}" }
                    _sideEffect.trySend(AppSideEffect.ShowError("Export failed: ${error.message}"))
                },
            )
        }
    }

    /**
     * Clean up resources when the ViewModel is no longer needed.
     * Should be called when the application window is closed.
     */
    fun cleanup() {
        logger.info { "MainViewModel cleanup: cancelling all coroutines" }
        activeRequestJobs.values.forEach { it.cancel() }
        activeRequestJobs.clear()
        viewModelScope.cancel()
    }
}
