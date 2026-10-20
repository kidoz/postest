package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter

class HistoryManager(
    private val useCase: ManageHistoryUseCase,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    val history = useCase.history

    suspend fun addHistoryEntry(entry: HistoryEntry) = useCase.addHistoryEntry(entry)

    fun clearHistory() {
        scope.launch {
            try {
                useCase.clearHistory()
                effects.emit(AppSideEffect.ShowToast("History cleared"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effects.emit(AppSideEffect.ShowError("History could not be cleared"))
            }
        }
    }
}
