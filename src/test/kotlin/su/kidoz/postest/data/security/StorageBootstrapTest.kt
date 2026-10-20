package su.kidoz.postest.data.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.data.repository.HistoryRepository
import su.kidoz.postest.data.repository.SessionRepository
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.model.Variable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageBootstrapTest {
    private class MemoryKeyStore : NativeKeyStore {
        val keys = mutableMapOf<String, ByteArray>()
        var writes = 0

        override fun read(vaultId: String): ByteArray? = keys[vaultId]?.copyOf()

        override fun write(
            vaultId: String,
            key: ByteArray,
        ) {
            writes++
            keys[vaultId] = key.copyOf()
        }
    }

    @Test
    fun `every repository persists encrypted user content including ordinary variables and backups`() =
        runTest {
            withDirectory { directory ->
                val keys = MemoryKeyStore()
                val marker = "EXAMPLE-plaintext-marker-3e6bd4"
                val bootstrap = StorageBootstrap(directory, keys)
                val storage = bootstrap.open(initialMode = StorageMode.ENCRYPTED)
                val recovery = checkNotNull(storage.codec.recoveryKey())
                val database = PostestDatabase(storage.driver)
                val collections = CollectionRepository(database, storage.codec)
                val environments = EnvironmentRepository(database, storage.codec)
                val history = HistoryRepository(database, storage.codec)
                val sessions = SessionRepository(database, storage.codec)
                val request = HttpRequest(name = marker, url = "https://example.test/$marker", auth = AuthConfig.Bearer(marker))
                val collection = collections.createCollection(marker, marker)
                collections.addRequestToCollection(collection.id, request, marker, null)
                val folder = collections.addFolderToCollection(collection.id, marker, marker, null)
                collections.renameCollectionItem(folder.id, marker)
                collections.renameCollection(collection.id, marker)
                collections.updateCollection(
                    collection.copy(
                        name = marker,
                        auth = AuthConfig.Basic(marker, marker),
                        variables = listOf(Variable(key = marker, value = marker)),
                    ),
                )
                val environment = Environment(name = marker, isActive = true, variables = listOf(Variable(key = marker, value = marker)))
                environments.saveEnvironment(environment)
                environments.updateEnvironment(environment)
                val response =
                    HttpResponse(
                        statusCode = 200,
                        statusText = marker,
                        headers = mapOf(marker to listOf(marker)),
                        body = marker,
                        contentType = marker,
                        size = marker.length.toLong(),
                        time = ResponseTime(total = 1),
                    )
                val entry = HistoryEntry(request = request, response = response, errorMessage = marker, duration = 1)
                history.addHistoryEntry(entry)
                val session =
                    DraftSession(tabs = listOf(DraftTab("tab", request, true, response = response, error = marker)), activeTabId = "tab")
                sessions.save(session)
                val invalid = "{invalid-$marker"
                database.postestQueries.saveWorkspaceSession(
                    storage.codec.encode("WorkspaceSession", "current", "snapshot_json", invalid),
                )
                assertFailsWith<IllegalStateException> { sessions.load() }
                assertEquals(
                    invalid,
                    storage.codec.decode(
                        "WorkspaceSession",
                        "backup",
                        "snapshot_json",
                        database.postestQueries.selectWorkspaceSessionBackup().executeAsOne(),
                    ),
                )
                sessions.save(session)
                storage.acknowledgeRecoveryKey()
                storage.close()

                Files.list(directory).use { paths ->
                    paths.filter { Files.isRegularFile(it) }.forEach { path ->
                        val bytes = Files.readAllBytes(path).toString(Charsets.ISO_8859_1)
                        assertFalse(bytes.contains(marker), "Plaintext found in ${path.fileName}")
                        assertFalse(bytes.contains(recovery), "Recovery key found in ${path.fileName}")
                    }
                }
                StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { reopened ->
                    assertFalse(reopened.needsRecoveryAcknowledgement)
                    val restored = PostestDatabase(reopened.driver)
                    assertEquals(marker, CollectionRepository(restored, reopened.codec).loadCollections().single().name)
                    assertEquals(environment, EnvironmentRepository(restored, reopened.codec).getActiveEnvironment())
                    assertEquals(entry, HistoryRepository(restored, reopened.codec).loadHistory().single())
                    assertEquals(session, SessionRepository(restored, reopened.codec).load())
                }
            }
        }

    @Test
    fun `legacy plaintext database migrates once and all old sidecars are retired`() {
        withDirectory { directory ->
            val legacy = directory.resolve("postest.db")
            val marker = "EXAMPLE-migration-marker-8a2f"
            JdbcSqliteDriver("jdbc:sqlite:$legacy").use { driver ->
                PostestDatabase.Schema.create(driver)
                val queries = PostestDatabase(driver).postestQueries
                queries.insertCollection("collection", marker, marker, marker, null, 1, 2)
                queries.saveWorkspaceSession(marker)
                queries.saveWorkspaceSessionBackup(marker)
                // Both historically optional columns may be absent in older installations.
                driver.execute(null, "ALTER TABLE Collection DROP COLUMN variables_json", 0)
                driver.execute(null, "ALTER TABLE HistoryEntry DROP COLUMN error_message", 0)
            }
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { storage ->
                val queries = PostestDatabase(storage.driver).postestQueries
                val row = queries.selectAllCollections().executeAsOne()
                assertEquals(marker, storage.codec.decode("Collection", row.id, "auth_json", row.auth_json!!))
                assertEquals(
                    marker,
                    storage.codec.decode(
                        "WorkspaceSession",
                        "backup",
                        "snapshot_json",
                        queries.selectWorkspaceSessionBackup().executeAsOne(),
                    ),
                )
                assertFalse(Files.exists(legacy))
            }
            val bytes = Files.readAllBytes(directory.resolve(StorageBootstrap.DATABASE_NAME)).toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains(marker))
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            assertEquals(1, keys.writes)
        }
    }

    @Test
    fun `missing and locked keys never create a replacement or modify saved ciphertext`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            val path = directory.resolve(StorageBootstrap.DATABASE_NAME)
            val saved = Files.readAllBytes(path)
            keys.keys.clear()
            assertEquals(
                StorageProblem.KEY_MISSING,
                assertFailsWith<StorageException> {
                    StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED)
                }.problem,
            )
            assertEquals(1, keys.writes)
            assertTrue(saved.contentEquals(Files.readAllBytes(path)))
            val locked = mockk<NativeKeyStore>()
            every { locked.read(any()) } throws StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
            assertEquals(
                StorageProblem.KEY_STORE_UNAVAILABLE,
                assertFailsWith<StorageException> { StorageBootstrap(directory, locked).open(initialMode = StorageMode.ENCRYPTED) }.problem,
            )
            verify(exactly = 0) { locked.write(any(), any()) }
            assertTrue(saved.contentEquals(Files.readAllBytes(path)))
        }
    }

    @Test
    fun `recovery replaces a lost or wrong stored key only after authentication`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            val recovery = StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { it.codec.recoveryKey() }
            val id = keys.keys.keys.single()
            keys.keys[id] = RecordCipher.newKey()
            assertEquals(
                StorageProblem.INVALID_DATA,
                assertFailsWith<StorageException> { StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED) }.problem,
            )
            val writes = keys.writes
            assertFailsWith<StorageException> {
                StorageBootstrap(directory, keys).open(RecordCipher("wrong", RecordCipher.newKey()).use { it.recoveryKey() })
            }
            assertEquals(writes, keys.writes)
            StorageBootstrap(directory, keys).open(recovery).close()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            assertEquals(writes + 1, keys.writes)
        }
    }

    @Test
    fun `exclusive lock prevents a second application from opening storage`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use {
                assertEquals(
                    StorageProblem.ALREADY_OPEN,
                    assertFailsWith<StorageException> {
                        StorageBootstrap(
                            directory,
                            keys,
                        ).open(initialMode = StorageMode.ENCRYPTED)
                    }.problem,
                )
            }
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
        }
    }

    @Test
    fun `completed stage is recovered without generating another key`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            Files.move(
                directory.resolve(StorageBootstrap.DATABASE_NAME),
                directory.resolve("${StorageBootstrap.DATABASE_NAME}.pending"),
                StandardCopyOption.ATOMIC_MOVE,
            )
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            assertEquals(1, keys.writes)
            assertTrue(Files.exists(directory.resolve(StorageBootstrap.DATABASE_NAME)))
        }
    }

    @Test
    fun `failed migration retains original database and can be retried`() {
        withDirectory { directory ->
            val legacy = directory.resolve("postest.db")
            JdbcSqliteDriver("jdbc:sqlite:$legacy").use { driver ->
                PostestDatabase.Schema.create(driver)
                driver.execute(null, "DROP TABLE Environment", 0)
            }
            val original = Files.readAllBytes(legacy)
            val keys = MemoryKeyStore()
            assertFailsWith<StorageException> { StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED) }
            assertTrue(original.contentEquals(Files.readAllBytes(legacy)))
            assertFalse(Files.exists(directory.resolve(StorageBootstrap.DATABASE_NAME)))
        }
    }

    @Test
    fun `startup rejects substituted records instead of starting autosave`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { storage ->
                val queries = PostestDatabase(storage.driver).postestQueries
                queries.saveWorkspaceSession(storage.codec.encode("WorkspaceSession", "backup", "snapshot_json", "EXAMPLE"))
            }
            assertEquals(
                StorageProblem.INVALID_DATA,
                assertFailsWith<StorageException> { StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED) }.problem,
            )
        }
    }

    @Test
    fun `unknown legacy tables prevent migration and plaintext cleanup`() {
        withDirectory { directory ->
            val legacy = directory.resolve("postest.db")
            JdbcSqliteDriver("jdbc:sqlite:$legacy").use { driver ->
                PostestDatabase.Schema.create(driver)
                driver.execute(null, "CREATE TABLE FutureData (id TEXT, payload TEXT)", 0)
            }
            val original = Files.readAllBytes(legacy)
            assertFailsWith<StorageException> { StorageBootstrap(directory, MemoryKeyStore()).open(initialMode = StorageMode.ENCRYPTED) }
            assertTrue(original.contentEquals(Files.readAllBytes(legacy)))
        }
    }

    @Test
    fun `new text columns and tables require an explicit encryption coverage decision`() {
        withDirectory { directory ->
            StorageBootstrap(directory, MemoryKeyStore()).open(initialMode = StorageMode.ENCRYPTED).use {
                DriverManager.getConnection("jdbc:sqlite:${directory.resolve(StorageBootstrap.DATABASE_NAME)}").use { connection ->
                    val structural = setOf("id", "collection_id", "parent_folder_id", "type", "environment_id")
                    connection.createStatement().use { query ->
                        query.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { tables ->
                            while (tables.next()) {
                                val table = tables.getString(1)
                                if (table == "ProtectedStorage") continue
                                assertTrue(table in contentColumns, "Table $table needs an encryption coverage decision")
                                connection.createStatement().use { columns ->
                                    columns.executeQuery("PRAGMA table_info($table)").use { fields ->
                                        while (fields.next()) {
                                            val column = fields.getString("name")
                                            if (fields.getString("type") == "TEXT" && column !in structural) {
                                                assertTrue(column in contentColumns.getValue(table), "$table.$column is unprotected")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `legacy database without workspace table gains encrypted session support`() {
        withDirectory { directory ->
            val legacy = directory.resolve("postest.db")
            JdbcSqliteDriver("jdbc:sqlite:$legacy").use { driver ->
                PostestDatabase.Schema.create(driver)
                driver.execute(null, "DROP TABLE WorkspaceSession", 0)
            }
            StorageBootstrap(directory, MemoryKeyStore()).open(initialMode = StorageMode.ENCRYPTED).use { storage ->
                val repository = SessionRepository(PostestDatabase(storage.driver), storage.codec)
                kotlin.test.assertNull(repository.load())
                val session = DraftSession(tabs = listOf(DraftTab("tab", HttpRequest(), true)), activeTabId = "tab")
                repository.save(session)
                assertEquals(session, repository.load())
            }
        }
    }

    @Test
    fun `previous encrypted filename upgrades without changing its key and retains an encrypted backup`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            val marker = "EXAMPLE-previous-version"
            val recovery =
                StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { storage ->
                    PostestDatabase(
                        storage.driver,
                    ).postestQueries.saveWorkspaceSession(storage.codec.encode("WorkspaceSession", "current", "snapshot_json", marker))
                    storage.codec.recoveryKey()
                }
            val previous = directory.resolve("postest-protected.db")
            Files.move(directory.resolve(StorageBootstrap.DATABASE_NAME), previous)
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use { storage ->
                assertEquals(recovery, storage.codec.recoveryKey())
                val value = PostestDatabase(storage.driver).postestQueries.selectWorkspaceSession().executeAsOne()
                assertEquals(marker, storage.codec.decode("WorkspaceSession", "current", "snapshot_json", value))
            }
            assertEquals(1, keys.writes)
            assertFalse(Files.exists(previous))
            val backup = directory.resolve("posttest.db.bak")
            assertTrue(Files.exists(backup))
            assertFalse(Files.readAllBytes(backup).toString(Charsets.ISO_8859_1).contains(marker))
        }
    }

    @Test
    fun `older completed staging filename is recovered and existing backups are preserved`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            Files.move(directory.resolve(StorageBootstrap.DATABASE_NAME), directory.resolve("postest-protected.pending.db"))
            val backup = directory.resolve("posttest.db.bak")
            Files.writeString(backup, "EXAMPLE-existing-backup")
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            assertEquals("EXAMPLE-existing-backup", Files.readString(backup))
            assertTrue(Files.exists(directory.resolve("posttest.db.bak.1")))
            assertFalse(Files.exists(directory.resolve("postest-protected.pending.db")))
            assertEquals(1, keys.writes)
        }
    }

    @Test
    fun `missing key for previous filename preserves original and never creates a fresh database`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            val previous = directory.resolve("postest-protected.db")
            Files.move(directory.resolve(StorageBootstrap.DATABASE_NAME), previous)
            keys.keys.clear()
            assertEquals(
                StorageProblem.KEY_MISSING,
                assertFailsWith<StorageException> {
                    StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED)
                }.problem,
            )
            assertTrue(Files.exists(previous))
            assertFalse(Files.exists(directory.resolve(StorageBootstrap.DATABASE_NAME)))
            assertEquals(1, keys.writes)
        }
    }

    @Test
    fun `restoring an upgrade backup does not remove an unrelated legacy database`() {
        withDirectory { directory ->
            val keys = MemoryKeyStore()
            JdbcSqliteDriver("jdbc:sqlite:${directory.resolve("postest.db")}").use { PostestDatabase.Schema.create(it) }
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            val restore = directory.resolve("restore")
            Files.createDirectories(restore)
            Files.copy(directory.resolve("posttest.db.bak"), restore.resolve(StorageBootstrap.DATABASE_NAME))
            val unrelated = restore.resolve("postest.db")
            Files.writeString(unrelated, "EXAMPLE-unrelated-legacy")
            StorageBootstrap(restore, keys).open(initialMode = StorageMode.ENCRYPTED).close()
            assertEquals("EXAMPLE-unrelated-legacy", Files.readString(unrelated))
        }
    }

    private fun withDirectory(action: suspend (Path) -> Unit) =
        kotlinx.coroutines.runBlocking {
            val root = Path.of("build", "protected-storage-tests")
            Files.createDirectories(root)
            val directory = Files.createTempDirectory(root, "vault-")
            try {
                action(directory)
            } finally {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
}
