package su.kidoz.postest.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.security.RecordCodec
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.port.DraftSessionStore

/** Stores one atomic workspace snapshot in the application's existing database. */
class SessionRepository(
    private val database: PostestDatabase,
    private val codec: RecordCodec,
) : DraftSessionStore {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override suspend fun load(): DraftSession? =
        withContext(Dispatchers.IO) {
            val stored = database.postestQueries.selectWorkspaceSession().executeAsOneOrNull() ?: return@withContext null
            val plaintext = codec.decode("WorkspaceSession", "current", "snapshot_json", stored)
            try {
                val session = json.decodeFromString<DraftSession>(plaintext)
                require(session.version == 1) { "Unsupported draft session version" }
                require(session.tabs.isNotEmpty()) { "Empty draft session" }
                require(session.tabs.all { it.id.isNotBlank() } && session.tabs.distinctBy { it.id }.size == session.tabs.size) {
                    "Invalid draft tab identifiers"
                }
                session
            } catch (_: Exception) {
                database.postestQueries.saveWorkspaceSessionBackup(codec.encode("WorkspaceSession", "backup", "snapshot_json", plaintext))
                // Serialization errors can contain request content; expose only a fixed message.
                error("Saved drafts are invalid or unsupported")
            }
        }

    override suspend fun save(session: DraftSession) {
        withContext(Dispatchers.IO) {
            database.postestQueries.saveWorkspaceSession(
                codec.encode("WorkspaceSession", "current", "snapshot_json", json.encodeToString(session)),
            )
        }
    }
}
