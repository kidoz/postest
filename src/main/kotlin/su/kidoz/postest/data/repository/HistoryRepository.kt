package su.kidoz.postest.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import java.util.UUID

private val logger = KotlinLogging.logger {}

class HistoryRepository(
    private val database: PostestDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: Flow<List<HistoryEntry>> = _history.asStateFlow()

    /**
     * Initial load of history from database.
     * Called once at startup.
     */
    suspend fun loadHistory() =
        withContext(Dispatchers.IO) {
            val dbHistory = database.postestQueries.selectAllHistory().executeAsList()
            val result =
                dbHistory.mapNotNull { dbEntry ->
                    runCatching {
                        HistoryEntry(
                            id = dbEntry.id,
                            request = json.decodeFromString<HttpRequest>(dbEntry.request_json),
                            response = dbEntry.response_json?.let { json.decodeFromString<HttpResponse>(it) },
                            errorMessage = dbEntry.error_message,
                            duration = dbEntry.duration,
                            timestamp = dbEntry.timestamp,
                        )
                    }.getOrElse { error ->
                        logger.warn(error) { "Skipping corrupted history entry ${dbEntry.id}" }
                        null
                    }
                }
            _history.value = result
            result
        }

    /**
     * Adds a history entry.
     * Uses incremental update - prepends to existing list instead of full reload.
     */
    suspend fun addHistoryEntry(entry: HistoryEntry) =
        withContext(Dispatchers.IO) {
            val entryId = entry.id.ifBlank { UUID.randomUUID().toString() }
            val entryWithId = if (entry.id.isBlank()) entry.copy(id = entryId) else entry

            database.postestQueries.insertHistoryEntry(
                id = entryId,
                request_json = json.encodeToString(entry.request),
                response_json = entry.response?.let { json.encodeToString(it) },
                error_message = entry.errorMessage,
                duration = entry.duration,
                timestamp = entry.timestamp,
            )

            // Incremental update: prepend new entry (history is ordered newest first)
            _history.update { currentHistory ->
                listOf(entryWithId) + currentHistory
            }
        }

    /**
     * Deletes a history entry.
     * Uses incremental update - filters out the entry instead of full reload.
     */
    suspend fun deleteHistoryEntry(entryId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteHistoryEntry(entryId)

            // Incremental update: remove the deleted entry
            _history.update { currentHistory ->
                currentHistory.filter { it.id != entryId }
            }
        }

    /**
     * Clears all history.
     * Already optimized - just clears the in-memory list.
     */
    suspend fun clearHistory() =
        withContext(Dispatchers.IO) {
            database.postestQueries.clearHistory()
            _history.value = emptyList()
        }
}
