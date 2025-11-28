package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater

/**
 * Manager for request/response history.
 * Handles: history loading, adding entries, clearing history.
 */
class HistoryManager(
    private val manageHistoryUseCase: ManageHistoryUseCase,
    private val stateUpdater: StateUpdater<AppState>,
    private val sideEffectEmitter: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    /**
     * Initializes history by subscribing to the history flow.
     */
    fun initialize() {
        scope.launch {
            manageHistoryUseCase.history.collect { history ->
                stateUpdater.update { it.copy(history = history) }
            }
        }

        scope.launch {
            manageHistoryUseCase.loadHistory()
        }
    }

    /**
     * Adds a new entry to history.
     */
    suspend fun addHistoryEntry(entry: HistoryEntry) {
        manageHistoryUseCase.addHistoryEntry(entry)
    }

    /**
     * Clears all history entries.
     */
    fun clearHistory() {
        scope.launch {
            manageHistoryUseCase.clearHistory()
            sideEffectEmitter.emit(AppSideEffect.ShowToast("History cleared"))
        }
    }
}
