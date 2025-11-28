package su.kidoz.postest.domain.usecase

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.data.repository.HistoryRepository
import su.kidoz.postest.domain.model.HistoryEntry

class ManageHistoryUseCase(
    private val historyRepository: HistoryRepository,
) {
    val history: Flow<List<HistoryEntry>> = historyRepository.history

    suspend fun loadHistory(): List<HistoryEntry> = historyRepository.loadHistory()

    suspend fun addHistoryEntry(entry: HistoryEntry) {
        historyRepository.addHistoryEntry(entry)
    }

    suspend fun deleteHistoryEntry(entryId: String) {
        historyRepository.deleteHistoryEntry(entryId)
    }

    suspend fun clearHistory() {
        historyRepository.clearHistory()
    }
}
