package su.kidoz.postest.viewmodel

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.EnvironmentSnapshot
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.containsItem
import su.kidoz.postest.domain.port.StorageAccessException
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.viewmodel.manager.CollectionManager
import su.kidoz.postest.viewmodel.manager.EnvironmentManager
import su.kidoz.postest.viewmodel.manager.HistoryManager
import su.kidoz.postest.viewmodel.manager.ImportExportManager
import su.kidoz.postest.viewmodel.manager.RequestExecutionManager
import su.kidoz.postest.viewmodel.manager.SessionManager
import su.kidoz.postest.viewmodel.manager.TabManager
import java.io.File
import java.util.UUID
import kotlin.reflect.KClass

private val logger = KotlinLogging.logger {}

// State
data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val request: HttpRequest = HttpRequest(),
    val collectionItemId: String? = null,
    val response: su.kidoz.postest.domain.model.HttpResponse? = null,
    val isLoading: Boolean = false,
    val executionId: String? = null,
    val isDirty: Boolean = false,
    val error: String? = null,
    val bodyDrafts: Map<KClass<out RequestBody>, RequestBody> = emptyMap(),
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
    val initializationError: String? = null,
    val isClosing: Boolean = false,
    val pendingCloseTabIds: List<String> = emptyList(),
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
    manageSessionUseCase: ManageSessionUseCase,
) : SideEffectEmitter {
    private val supervisorJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(supervisorJob + Dispatchers.Main)

    private val workspace = WorkspaceStore()

    private data class PresentationState(
        val darkTheme: Boolean = false,
        val environmentDialog: Boolean = false,
        val collectionDialog: Boolean = false,
        val startupError: String? = null,
    )

    private val presentation = MutableStateFlow(PresentationState())
    private val _sideEffect = Channel<AppSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()
    private val tabManager = TabManager(workspace)
    private val sessionManager = SessionManager(manageSessionUseCase, workspace.state, this, viewModelScope)
    private val historyManager = HistoryManager(manageHistoryUseCase, this, viewModelScope)
    private val requestExecutionManager = RequestExecutionManager(executeRequestUseCase, workspace, historyManager, this, viewModelScope)
    private val collectionManager =
        CollectionManager(manageCollectionsUseCase, this, viewModelScope) {
            presentation.update { it.copy(collectionDialog = false) }
        }
    private val environmentManager = EnvironmentManager(manageEnvironmentsUseCase, this, viewModelScope)
    private val importExportManager =
        ImportExportManager(importCollectionUseCase, exportCollectionUseCase, { state.value }, this, viewModelScope)

    private data class Observed<T>(
        val value: T,
        val loaded: Boolean = false,
        val error: String? = null,
    )

    private fun <T> observe(
        source: Flow<T>,
        initial: T,
        name: String,
    ): Flow<Observed<T>> =
        source
            .map { Observed(it, loaded = true) }
            .onStart { emit(Observed(initial)) }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is StorageAccessException) blockStorage()
                val message = "$name could not be loaded"
                this@MainViewModel.emit(AppSideEffect.ShowError(message))
                emit(Observed(initial, loaded = true, error = message))
            }

    private val savedData =
        combine(
            observe(collectionManager.collections, emptyList(), "Collections"),
            observe(environmentManager.snapshot, EnvironmentSnapshot(emptyList()), "Environments"),
            observe(historyManager.history, emptyList(), "History"),
        ) { collections, environments, history -> Triple(collections, environments, history) }

    val state: StateFlow<AppState> =
        combine(workspace.state, presentation, savedData) { tabs, ui, data ->
            val (collections, environments, history) = data
            AppState(
                tabs = tabs.tabs,
                activeTabId = tabs.activeTabId,
                pendingCloseTabIds = tabs.pendingCloseTabIds,
                collections = collections.value,
                environments = environments.value.environments,
                activeEnvironmentId = environments.value.activeEnvironmentId,
                history = history.value,
                isDarkTheme = ui.darkTheme,
                showEnvironmentDialog = ui.environmentDialog,
                showNewCollectionDialog = ui.collectionDialog,
                isInitialized = ui.startupError == null && tabs.isRestored && collections.loaded && environments.loaded && history.loaded,
                initializationError = ui.startupError ?: collections.error ?: environments.error ?: history.error,
                isClosing = tabs.isClosing,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppState(
                tabs = workspace.state.value.tabs,
                activeTabId = workspace.state.value.activeTabId,
            ),
        )

    private val closing = Mutex()
    private val initialWorkspace = workspace.state.value.toDraftSession()
    private val startup: Job =
        viewModelScope.launch {
            try {
                val session = sessionManager.load()
                workspace.dispatch(WorkspaceCommand.Restore(session, initialWorkspace))
                sessionManager.initialize()
            } catch (_: StorageAccessException) {
                blockStorage()
            }
        }

    private fun blockStorage() {
        sessionManager.blockWrites()
        presentation.update {
            it.copy(startupError = "Protected storage cannot be read. Close Postest and retry unlocking it. Autosave is blocked.")
        }
    }

    init {
        viewModelScope.launch {
            workspace.state.collect { requestExecutionManager.cancelMissingTabs(it.tabs.map { tab -> tab.id }.toSet()) }
        }
    }

    // SideEffectEmitter implementation
    override fun emit(effect: AppSideEffect) {
        _sideEffect.trySend(effect)
    }

    // ========== Tab Operations (delegated to TabManager) ==========

    fun updateRequest(request: HttpRequest) {
        val activeTabId = workspace.state.value.activeTabId ?: return
        tabManager.updateRequest(activeTabId, request)
    }

    fun newTab() {
        tabManager.newTab()
    }

    fun closeTab(tabId: String) {
        tabManager.closeTab(tabId)
    }

    fun closeAllTabs() {
        tabManager.closeAllTabs()
    }

    fun closeOtherTabs(tabId: String) {
        tabManager.closeOtherTabs(tabId)
    }

    fun closeTabsToLeft(tabId: String) {
        tabManager.closeTabsToLeft(tabId)
    }

    fun closeTabsToRight(tabId: String) {
        tabManager.closeTabsToRight(tabId)
    }

    fun discardPendingTabs() {
        tabManager.discardPendingTabs()
    }

    fun cancelTabClose() {
        tabManager.cancelTabClose()
    }

    fun selectTab(tabId: String) {
        tabManager.selectTab(tabId)
    }

    fun openRequest(
        request: HttpRequest,
        collectionItemId: String? = null,
    ) {
        tabManager.openRequest(request, collectionItemId)
    }

    fun openHistoryEntry(entry: HistoryEntry) {
        tabManager.openHistoryEntry(entry)
    }

    // ========== Request Operations (delegated to RequestExecutionManager) ==========

    fun sendRequest() {
        if (workspace.state.value.isClosing) return
        if (!state.value.isInitialized || state.value.initializationError != null) {
            emit(AppSideEffect.ShowError("Wait for workspace data to load before sending a request"))
            return
        }
        val activeTab = tabManager.getActiveTab() ?: return
        val variables =
            activeTab.collectionItemId
                ?.let { id ->
                    state.value.collections
                        .firstOrNull { it.containsItem(id) }
                        ?.variables
                }.orEmpty()
        environmentManager.captureEnvironment { environment ->
            requestExecutionManager.sendRequest(activeTab.id, activeTab.request, variables, ExecutionContext(environment, variables))
        }
    }

    fun cancelRequest() {
        val activeTabId = tabManager.getActiveTabId() ?: return
        requestExecutionManager.cancelRequest(activeTabId)
    }

    // ========== Collection Operations (delegated to CollectionManager) ==========

    fun showNewCollectionDialog() {
        presentation.update { it.copy(collectionDialog = true) }
    }

    fun hideNewCollectionDialog() {
        presentation.update { it.copy(collectionDialog = false) }
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

    fun addFolderToCollection(
        collectionId: String,
        name: String,
        parentFolderId: String? = null,
    ) {
        collectionManager.addFolderToCollection(collectionId, name, parentFolderId)
    }

    fun moveCollectionItem(
        itemId: String,
        targetFolderId: String?,
    ) {
        collectionManager.moveCollectionItem(itemId, targetFolderId)
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
        presentation.update { it.copy(environmentDialog = true) }
    }

    fun hideEnvironmentDialog() {
        presentation.update { it.copy(environmentDialog = false) }
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
        presentation.update { it.copy(darkTheme = !it.darkTheme) }
    }

    // ========== Cleanup ==========

    suspend fun prepareToClose(): Boolean =
        closing.withLock {
            startup.join()
            workspace.dispatch(WorkspaceCommand.BeginClosing)
            requestExecutionManager.quiesce()
            if (presentation.value.startupError != null) return@withLock true
            val saved = sessionManager.prepareToClose()
            if (!saved) workspace.dispatch(WorkspaceCommand.Resume)
            saved
        }

    fun cleanup() {
        logger.info { "MainViewModel cleanup: cancelling all coroutines" }
        requestExecutionManager.cleanup()
        viewModelScope.cancel()
    }

    suspend fun awaitCleanup() {
        cleanup()
        supervisorJob.join()
    }
}
