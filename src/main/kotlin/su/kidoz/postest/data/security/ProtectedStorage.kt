package su.kidoz.postest.data.security

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import su.kidoz.postest.data.db.PostestDatabase
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.sql.DriverManager

class ProtectedStorage internal constructor(
    val driver: SqlDriver,
    val cipher: RecordCipher,
    private val path: Path,
    private val lock: FileLock,
    private val lockChannel: FileChannel,
    var needsRecoveryAcknowledgement: Boolean,
) : AutoCloseable {
    fun acknowledgeRecoveryKey() {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE ProtectedStorage SET recovery_acknowledged = 1") }
        }
        needsRecoveryAcknowledgement = false
    }

    override fun close() {
        try {
            driver.close()
        } finally {
            cipher.close()
            lock.release()
            lockChannel.close()
        }
    }
}

internal fun createProtectedDriver(path: Path): SqlDriver =
    JdbcSqliteDriver("jdbc:sqlite:$path").apply {
        execute(null, "PRAGMA foreign_keys = ON", 0)
        execute(null, "PRAGMA secure_delete = ON", 0)
        execute(null, "CREATE INDEX IF NOT EXISTS idx_collection_item_collection_id ON CollectionItem(collection_id)", 0)
        execute(null, "CREATE INDEX IF NOT EXISTS idx_collection_item_parent_folder_id ON CollectionItem(parent_folder_id)", 0)
        execute(null, "CREATE INDEX IF NOT EXISTS idx_environment_variable_environment_id ON EnvironmentVariable(environment_id)", 0)
        execute(null, "CREATE INDEX IF NOT EXISTS idx_history_entry_timestamp ON HistoryEntry(timestamp)", 0)
    }

internal fun createProtectedSchema(path: Path) {
    JdbcSqliteDriver("jdbc:sqlite:$path").use { driver -> PostestDatabase.Schema.create(driver) }
}
