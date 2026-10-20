package su.kidoz.postest.domain.port

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.domain.model.*

interface HistoryRepository {
    val history: Flow<List<HistoryEntry>>

    suspend fun loadHistory(): List<HistoryEntry>

    suspend fun addHistoryEntry(entry: HistoryEntry)

    suspend fun deleteHistoryEntry(entryId: String)

    suspend fun clearHistory()
}
