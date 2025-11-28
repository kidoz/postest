package su.kidoz.postest.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    suspend fun addHistoryEntry(entry: HistoryEntry) =
        withContext(Dispatchers.IO) {
            database.postestQueries.insertHistoryEntry(
                id = entry.id.ifBlank { UUID.randomUUID().toString() },
                request_json = json.encodeToString(entry.request),
                response_json = entry.response?.let { json.encodeToString(it) },
                error_message = entry.errorMessage,
                duration = entry.duration,
                timestamp = entry.timestamp,
            )
            loadHistory()
        }

    suspend fun deleteHistoryEntry(entryId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteHistoryEntry(entryId)
            loadHistory()
        }

    suspend fun clearHistory() =
        withContext(Dispatchers.IO) {
            database.postestQueries.clearHistory()
            _history.value = emptyList()
        }
}
