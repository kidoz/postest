package su.kidoz.postest.data.repository

import app.cash.sqldelight.coroutines.asFlow
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.limitBodyForStorage
import java.util.UUID
import su.kidoz.postest.domain.port.HistoryRepository as HistoryRepositoryPort

private val logger = KotlinLogging.logger {}

class HistoryRepository(
    private val database: PostestDatabase,
) : HistoryRepositoryPort {
    private val json = Json { ignoreUnknownKeys = true }
    override val history: Flow<List<HistoryEntry>> =
        database.postestQueries
            .selectAllHistory()
            .asFlow()
            .map { loadHistory() }

    /**
     * Initial load of history from database.
     * Called once at startup.
     */
    override suspend fun loadHistory() =
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
                        logger.warn { "Skipping corrupted history entry ${dbEntry.id}" }
                        null
                    }
                }
            result
        }

    /**
     * Adds a history entry.
     * Observers read the committed database state.
     */
    override suspend fun addHistoryEntry(entry: HistoryEntry) {
        withContext(Dispatchers.IO) {
            val entryId = entry.id.ifBlank { UUID.randomUUID().toString() }
            val entryWithId = if (entry.id.isBlank()) entry.copy(id = entryId) else entry
            val storedEntry = entryWithId.copy(response = entryWithId.response?.limitBodyForStorage())

            database.transaction {
                database.postestQueries.insertHistoryEntry(
                    id = entryId,
                    request_json = json.encodeToString(storedEntry.request),
                    response_json = storedEntry.response?.let { json.encodeToString(it) },
                    error_message = storedEntry.errorMessage,
                    duration = storedEntry.duration,
                    timestamp = storedEntry.timestamp,
                )
                database.postestQueries.deleteOldHistory()
            }
        }
    }

    /**
     * Deletes a history entry.
     * Observers read the committed database state.
     */
    override suspend fun deleteHistoryEntry(entryId: String) {
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteHistoryEntry(entryId)
        }
    }

    /**
     * Clears all history.
     * Observers read the committed database state.
     */
    override suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            database.postestQueries.clearHistory()
        }
    }
}
