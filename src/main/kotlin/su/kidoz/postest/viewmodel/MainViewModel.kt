package su.kidoz.postest.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.viewmodel.manager.CollectionManager
import su.kidoz.postest.viewmodel.manager.EnvironmentManager
import su.kidoz.postest.viewmodel.manager.HistoryManager
import su.kidoz.postest.viewmodel.manager.ImportExportManager
import su.kidoz.postest.viewmodel.manager.RequestExecutionManager
import su.kidoz.postest.viewmodel.manager.TabManager
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

// State
data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val request: HttpRequest = HttpRequest(),
    val response: su.kidoz.postest.domain.model.HttpResponse? = null,
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

/**
 * Main ViewModel coordinating all feature managers.
 * Provides a thin delegation layer maintaining the same public API.
 */
class MainViewModel(
    executeRequestUseCase: ExecuteRequestUseCase,
    manageCollectionsUseCase: ManageCollectionsUseCase,
    manageEnvironmentsUseCase: ManageEnvironmentsUseCase,
    manageHistoryUseCase: ManageHistoryUseCase,
    importCollectionUseCase: ImportCollectionUseCase,
    exportCollectionUseCase: ExportCollectionUseCase,
) : SideEffectEmitter {
    private val supervisorJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(supervisorJob + Dispatchers.Main)

    private val _state =
        MutableStateFlow(
            AppState().let { state ->
                state.copy(activeTabId = state.tabs.firstOrNull()?.id)
            },
        )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _sideEffect = Channel<AppSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    // Shared state updater for all managers
    private val stateUpdater = StateUpdater<AppState> { transform -> _state.update(transform) }

    // Feature Managers
    private val tabManager = TabManager(stateUpdater) { _state.value }

    private val historyManager =
        HistoryManager(
            manageHistoryUseCase,
            stateUpdater,
            this,
            viewModelScope,
        )

    private val requestExecutionManager =
        RequestExecutionManager(
            executeRequestUseCase,
            tabManager,
            historyManager,
            this,
            viewModelScope,
        )

    private val collectionManager =
        CollectionManager(
            manageCollectionsUseCase,
            stateUpdater,
            this,
            { _state.value },
            viewModelScope,
        )

    private val environmentManager =
        EnvironmentManager(
            manageEnvironmentsUseCase,
            stateUpdater,
            this,
            { _state.value },
            viewModelScope,
        )

    private val importExportManager =
        ImportExportManager(
            importCollectionUseCase,
            exportCollectionUseCase,
            { _state.value },
            this,
            viewModelScope,
        )

    init {
        initialize()
    }

    private fun initialize() {
        // Initialize all managers
        collectionManager.initialize()
        environmentManager.initialize()
        historyManager.initialize()

        _state.update { it.copy(isInitialized = true) }
    }

    // SideEffectEmitter implementation
    override fun emit(effect: AppSideEffect) {
        _sideEffect.trySend(effect)
    }

    // ========== Tab Operations (delegated to TabManager) ==========

    fun updateRequest(request: HttpRequest) {
        val activeTabId = _state.value.activeTabId ?: return
        tabManager.updateRequest(activeTabId, request)
    }

    fun newTab() {
        tabManager.newTab()
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
    }

    fun selectTab(tabId: String) {
        tabManager.selectTab(tabId)
    }

    fun openRequest(request: HttpRequest) {
        tabManager.openRequest(request)
    }

    fun openHistoryEntry(entry: HistoryEntry) {
        tabManager.openHistoryEntry(entry)
    }

    // ========== Request Operations (delegated to RequestExecutionManager) ==========

    fun sendRequest() {
        val activeTab = tabManager.getActiveTab() ?: return
        requestExecutionManager.sendRequest(activeTab.id, activeTab.request)
    }

    fun cancelRequest() {
        val activeTabId = tabManager.getActiveTabId() ?: return
        requestExecutionManager.cancelRequest(activeTabId)
    }

    // ========== Collection Operations (delegated to CollectionManager) ==========

    fun showNewCollectionDialog() {
        collectionManager.showNewCollectionDialog()
    }

    fun hideNewCollectionDialog() {
        collectionManager.hideNewCollectionDialog()
    }

    fun createCollection(name: String) {
        collectionManager.createCollection(name)
    }

    fun deleteCollection(collectionId: String) {
        collectionManager.deleteCollection(collectionId)
    }

    fun renameCollection(
        collectionId: String,
        newName: String,
    ) {
        collectionManager.renameCollection(collectionId, newName)
    }

    fun addRequestToCollection(
        collection: RequestCollection,
        requestName: String,
    ) {
        val activeTab = tabManager.getActiveTab() ?: return
        collectionManager.addRequestToCollection(collection.id, activeTab.request, requestName)
    }

    fun deleteCollectionItem(itemId: String) {
        collectionManager.deleteCollectionItem(itemId)
    }

    fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) {
        collectionManager.renameCollectionItem(itemId, newName)
    }

    // ========== Environment Operations (delegated to EnvironmentManager) ==========

    fun showEnvironmentDialog() {
        environmentManager.showEnvironmentDialog()
    }

    fun hideEnvironmentDialog() {
        environmentManager.hideEnvironmentDialog()
    }

    fun selectEnvironment(environmentId: String?) {
        environmentManager.selectEnvironment(environmentId)
    }

    fun saveEnvironment(environment: Environment) {
        environmentManager.saveEnvironment(environment)
    }

    fun createEnvironment(name: String) {
        environmentManager.createEnvironment(name)
    }

    fun deleteEnvironment(environmentId: String) {
        environmentManager.deleteEnvironment(environmentId)
    }

    // ========== History Operations (delegated to HistoryManager) ==========

    fun clearHistory() {
        historyManager.clearHistory()
    }

    // ========== Import/Export Operations (delegated to ImportExportManager) ==========

    fun importCollection(file: File) {
        importExportManager.importCollection(file)
    }

    fun exportCollection(
        collectionId: String,
        file: File,
    ) {
        importExportManager.exportCollection(collectionId, file)
    }

    // ========== UI State ==========

    fun toggleTheme() {
        _state.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    // ========== Cleanup ==========

    fun cleanup() {
        logger.info { "MainViewModel cleanup: cancelling all coroutines" }
        requestExecutionManager.cleanup()
        viewModelScope.cancel()
    }
}
