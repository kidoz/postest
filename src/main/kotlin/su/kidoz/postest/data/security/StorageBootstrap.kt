package su.kidoz.postest.data.security

import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/** Opens storage before any repository is constructed. Never replaces a missing key for an existing vault. */
class StorageBootstrap(
    private val directory: Path,
    private val keyStore: NativeKeyStore,
) {
    var current: ProtectedStorage? = null
        private set

    @Synchronized
    fun open(recoveryKey: String? = null): ProtectedStorage {
        current?.let { return it }
        Files.createDirectories(directory)
        val channel = FileChannel.open(directory.resolve("postest.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock =
            try {
                channel.tryLock() ?: throw StorageException(StorageProblem.ALREADY_OPEN)
            } catch (_: OverlappingFileLockException) {
                channel.close()
                throw StorageException(StorageProblem.ALREADY_OPEN)
            } catch (error: Exception) {
                channel.close()
                throw error
            }
        var cipher: RecordCipher? = null
        var opened = false
        try {
            val path = directory.resolve(DATABASE_NAME)
            val stage = directory.resolve("$DATABASE_NAME.pending")
            val legacy = directory.resolve("postest.db")
            var previous: Path? = null
            if (!Files.exists(path)) {
                previous = previousDatabase()
                // A stage is never used for live writes. Complete stages are recoverable after a crash.
                if (!Files.exists(stage) || !isComplete(stage)) {
                    removeStage(stage)
                    if (previous != null) {
                        validateExisting(previous, recoveryKey).close()
                        // VACUUM INTO takes a consistent snapshot, including committed WAL content.
                        connect(previous).use { connection ->
                            connection.prepareStatement("VACUUM INTO ?").use {
                                it.setString(1, stage.toAbsolutePath().toString())
                                it.execute()
                            }
                        }
                    } else {
                        createStage(stage, legacy)
                    }
                }
                validateExisting(stage, recoveryKey).close()
                if (previous != null && vaultId(previous) != vaultId(stage)) {
                    throw StorageException(StorageProblem.MIGRATION_FAILED)
                }
                if (previous != null || Files.exists(legacy)) backupStage(stage)
                promote(stage, path)
            }
            cipher = validateExisting(path, recoveryKey)
            previous?.let {
                removeStage(it)
                syncDirectory()
            }
            retireLegacy(path, legacy)
            val acknowledged =
                connect(path).use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT recovery_acknowledged FROM ProtectedStorage").use {
                            it.next()
                            it.getInt(1) != 0
                        }
                    }
                }
            return ProtectedStorage(createProtectedDriver(path), cipher, path, lock, channel, !acknowledged).also {
                current = it
                opened =
                    true
            }
        } catch (error: StorageException) {
            throw error
        } catch (_: Exception) {
            throw StorageException(StorageProblem.MIGRATION_FAILED)
        } finally {
            if (!opened) {
                cipher?.close()
                lock.release()
                channel.close()
            }
        }
    }

    @Synchronized
    fun close() {
        current?.close()
        current = null
    }

    private fun previousDatabase(): Path? {
        val previous = directory.resolve("postest-protected.db")
        if (Files.exists(previous)) return previous
        return directory.resolve("postest-protected.pending.db").takeIf { Files.exists(it) && isComplete(it) }
    }

    private fun backupStage(stage: Path) {
        var backup = directory.resolve("$DATABASE_NAME.bak")
        var index = 1
        while (Files.exists(backup)) backup = directory.resolve("$DATABASE_NAME.bak.${index++}")
        val pending = directory.resolve("$DATABASE_NAME.bak.pending")
        removeStage(pending)
        Files.copy(stage, pending)
        // A restored backup must not retire another installation's plaintext source.
        connect(pending).use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE ProtectedStorage SET legacy_cleanup_pending = 0") }
        }
        promote(pending, backup)
    }

    private fun vaultId(path: Path): String =
        connect(path).use { connection ->
            connection.createStatement().use { query ->
                query.executeQuery("SELECT vault_id FROM ProtectedStorage WHERE id = 1").use {
                    check(it.next())
                    it.getString(1)
                }
            }
        }

    private fun createStage(
        stage: Path,
        legacy: Path,
    ) {
        val vaultId = UUID.randomUUID().toString()
        val key = RecordCipher.newKey()
        val cipher = RecordCipher(vaultId, key)
        try {
            keyStore.write(vaultId, key)
            val retrieved = keyStore.read(vaultId) ?: throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
            try {
                if (!java.security.MessageDigest.isEqual(key, retrieved)) throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
            } finally {
                retrieved.fill(0)
            }
            createProtectedSchema(stage)
            connect(stage).use { destination ->
                destination.autoCommit = false
                if (Files.exists(legacy)) {
                    connect(legacy).use { source ->
                        source.createStatement().use { it.execute("BEGIN EXCLUSIVE") }
                        try {
                            copyContent(source, destination, cipher)
                            validateContent(destination, cipher)
                            writeMetadata(destination, vaultId, cipher, true)
                            destination.commit()
                        } finally {
                            source.createStatement().use { it.execute("ROLLBACK") }
                        }
                    }
                } else {
                    writeMetadata(destination, vaultId, cipher, false)
                    destination.commit()
                }
            }
        } finally {
            key.fill(0)
            cipher.close()
        }
    }

    private fun copyContent(
        source: Connection,
        destination: Connection,
        cipher: RecordCipher,
    ) {
        source.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rows ->
                while (rows.next()) {
                    if (rows.getString(1) !in contentColumns) throw StorageException(StorageProblem.MIGRATION_FAILED)
                }
            }
        }
        contentColumns.forEach { (table, protectedColumns) ->
            val exists =
                source.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?").use {
                    it.setString(1, table)
                    it.executeQuery().use { result -> result.next() }
                }
            if (!exists) {
                // The workspace table was added after the original application schema.
                if (table != "WorkspaceSession") throw StorageException(StorageProblem.MIGRATION_FAILED)
                return@forEach
            }
            source.createStatement().use { query ->
                query.executeQuery("SELECT * FROM \"$table\"").use { rows ->
                    val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it) }
                    val supported =
                        destination.createStatement().use { statement ->
                            statement.executeQuery("PRAGMA table_info($table)").use { fields ->
                                buildSet { while (fields.next()) add(fields.getString("name")) }
                            }
                        }
                    if (columns.any { it !in supported }) throw StorageException(StorageProblem.MIGRATION_FAILED)
                    val quoted = columns.joinToString(",") { "\"$it\"" }
                    val placeholders = columns.joinToString(",") { "?" }
                    destination.prepareStatement("INSERT INTO \"$table\" ($quoted) VALUES ($placeholders)").use { insert ->
                        while (rows.next()) {
                            val id = rows.getString("id")
                            columns.forEachIndexed { index, column ->
                                val value = rows.getObject(index + 1)
                                insert.setObject(
                                    index + 1,
                                    if (column in protectedColumns && value != null) {
                                        cipher.encrypt(table, id, column, rows.getString(index + 1))
                                    } else {
                                        value
                                    },
                                )
                            }
                            insert.executeUpdate()
                        }
                    }
                    val count =
                        destination.createStatement().use { countQuery ->
                            countQuery.executeQuery("SELECT COUNT(*) FROM \"$table\"").use {
                                it.next()
                                it.getLong(1)
                            }
                        }
                    val sourceCount =
                        source.createStatement().use { countQuery ->
                            countQuery.executeQuery("SELECT COUNT(*) FROM \"$table\"").use {
                                it.next()
                                it.getLong(1)
                            }
                        }
                    check(count == sourceCount)
                }
            }
        }
    }

    private fun writeMetadata(
        connection: Connection,
        vaultId: String,
        cipher: RecordCipher,
        legacy: Boolean,
    ) {
        connection.createStatement().use {
            it.execute(
                """
                CREATE TABLE ProtectedStorage (
                    id INTEGER PRIMARY KEY CHECK(id = 1), version INTEGER NOT NULL,
                    vault_id TEXT NOT NULL, verifier TEXT NOT NULL,
                    recovery_acknowledged INTEGER NOT NULL DEFAULT 0,
                    legacy_cleanup_pending INTEGER NOT NULL, complete INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        connection.prepareStatement("INSERT INTO ProtectedStorage VALUES (1, 1, ?, ?, 0, ?, 1)").use {
            it.setString(1, vaultId)
            it.setString(2, cipher.encrypt("ProtectedStorage", "1", "verifier", VERIFIER))
            it.setInt(3, if (legacy) 1 else 0)
            it.executeUpdate()
        }
    }

    private fun validateExisting(
        path: Path,
        recoveryKey: String?,
    ): RecordCipher =
        connect(path).use { connection ->
            val vaultId =
                connection.createStatement().use { query ->
                    query.executeQuery("SELECT version, vault_id, complete FROM ProtectedStorage WHERE id = 1").use {
                        if (!it.next() || it.getInt(1) != 1 || it.getInt(3) != 1) throw StorageException(StorageProblem.INVALID_DATA)
                        it.getString(2)
                    }
                }
            val key =
                if (recoveryKey == null) {
                    keyStore.read(vaultId) ?: throw StorageException(StorageProblem.KEY_MISSING)
                } else {
                    RecordCipher.parseRecoveryKey(recoveryKey)
                }
            val cipher =
                try {
                    RecordCipher(vaultId, key)
                } catch (_: IllegalArgumentException) {
                    key.fill(0)
                    throw StorageException(StorageProblem.INVALID_DATA)
                }
            var authenticated = false
            try {
                val verifier =
                    connection.createStatement().use { query ->
                        query.executeQuery("SELECT verifier FROM ProtectedStorage WHERE id = 1").use {
                            it.next()
                            it.getString(1)
                        }
                    }
                if (cipher.decrypt("ProtectedStorage", "1", "verifier", verifier) != VERIFIER) {
                    throw StorageException(StorageProblem.INVALID_DATA)
                }
                validateContent(connection, cipher)
                // Only authenticated recovery material is allowed to replace the stored key.
                if (recoveryKey != null) {
                    keyStore.write(vaultId, key)
                    val saved = keyStore.read(vaultId) ?: throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
                    try {
                        if (!java.security.MessageDigest.isEqual(key, saved)) throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
                    } finally {
                        saved.fill(0)
                    }
                }
                authenticated = true
                cipher
            } catch (error: StorageException) {
                throw error
            } catch (_: Exception) {
                throw StorageException(StorageProblem.INVALID_DATA)
            } finally {
                key.fill(0)
                if (!authenticated) cipher.close()
            }
        }

    private fun validateContent(
        connection: Connection,
        cipher: RecordCipher,
    ) {
        contentColumns.forEach { (table, columns) ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM \"$table\"").use { rows ->
                    while (rows.next()) {
                        columns.forEach { column ->
                            rows.getString(column)?.let { cipher.decrypt(table, rows.getString("id"), column, it) }
                        }
                    }
                }
            }
        }
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_check").use { if (it.next()) throw StorageException(StorageProblem.INVALID_DATA) }
            statement.executeQuery("PRAGMA integrity_check").use {
                if (!it.next() || it.getString(1) != "ok") throw StorageException(StorageProblem.INVALID_DATA)
            }
        }
    }

    private fun retireLegacy(
        path: Path,
        legacy: Path,
    ) {
        connect(path).use { connection ->
            val pending =
                connection.createStatement().use { query ->
                    query.executeQuery("SELECT legacy_cleanup_pending FROM ProtectedStorage").use {
                        it.next()
                        it.getInt(1) != 0
                    }
                }
            if (pending) {
                try {
                    // Remove sidecars before the main file; retries remain safe after a partial cleanup.
                    listOf("-wal", "-shm", "-journal", "").forEach { Files.deleteIfExists(Path.of("$legacy$it")) }
                    syncDirectory()
                    connection.createStatement().use { it.executeUpdate("UPDATE ProtectedStorage SET legacy_cleanup_pending = 0") }
                } catch (_: Exception) {
                    throw StorageException(StorageProblem.LEGACY_CLEANUP_FAILED)
                }
            }
        }
    }

    private fun promote(
        stage: Path,
        destination: Path,
    ) {
        FileChannel.open(stage, StandardOpenOption.WRITE).use { it.force(true) }
        Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE)
        syncDirectory()
    }

    private fun syncDirectory() {
        if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun isComplete(path: Path): Boolean =
        try {
            connect(path).use { connection ->
                connection.createStatement().use { query ->
                    query.executeQuery("SELECT complete FROM ProtectedStorage WHERE id = 1").use { it.next() && it.getInt(1) == 1 }
                }
            }
        } catch (_: Exception) {
            false
        }

    private fun removeStage(stage: Path) {
        listOf("-wal", "-shm", "-journal", "").forEach { Files.deleteIfExists(Path.of("$stage$it")) }
    }

    private fun connect(path: Path): Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    companion object {
        const val DATABASE_NAME = "posttest.db"
        private const val VERIFIER = "Postest protected storage version 1"
    }
}
