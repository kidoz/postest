package su.kidoz.postest.data.security

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.data.repository.HistoryRepository
import su.kidoz.postest.data.repository.SessionRepository
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.Variable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageModeTest {
    @Test
    fun `fresh startup waits for an explicit choice without creating a database or key`() =
        withDirectory { directory ->
            val keys = mockk<NativeKeyStore>()
            val bootstrap = StorageBootstrap(directory, keys)
            repeat(2) { assertFailsWith<StorageSetupRequiredException> { bootstrap.open() } }
            assertNull(bootstrap.current)
            assertFalse(Files.exists(directory.resolve(StorageBootstrap.DATABASE_NAME)))
            assertFalse(Files.exists(directory.resolve("posttest.db.pending")))
            bootstrap.open(initialMode = StorageMode.UNENCRYPTED)
            bootstrap.close()
            verify { keys wasNot Called }
        }

    @Test
    fun `unencrypted repositories round trip on restart without any key store access`() =
        withDirectory { directory ->
            val keys = mockk<NativeKeyStore>()
            val marker = "EXAMPLE-unencrypted-content"
            val request = HttpRequest(name = marker, auth = AuthConfig.Bearer(marker), createdAt = 1, updatedAt = 2)
            val session = DraftSession(tabs = listOf(DraftTab(id = "tab", request = request, isDirty = true)), activeTabId = "tab")
            val bootstrap = StorageBootstrap(directory, keys)
            val storage = bootstrap.open(initialMode = StorageMode.UNENCRYPTED)
            assertEquals(StorageMode.UNENCRYPTED, storage.mode)
            assertFalse(storage.needsRecoveryAcknowledgement)
            assertNull(storage.codec.recoveryKey())
            val database = PostestDatabase(storage.driver)
            val collections = CollectionRepository(database, storage.codec)
            val collection = collections.createCollection(marker, marker)
            collections.addRequestToCollection(collection.id, request, marker, null)
            val environments = EnvironmentRepository(database, storage.codec)
            val environment = environments.createEnvironment(marker)
            environments.updateEnvironment(environment.copy(variables = listOf(Variable(key = marker, value = marker))))
            HistoryRepository(database, storage.codec).addHistoryEntry(HistoryEntry(request = request, response = null, duration = 1))
            SessionRepository(database, storage.codec).save(session)
            assertEquals(
                marker,
                database.postestQueries
                    .selectAllCollections()
                    .executeAsOne()
                    .name,
            )
            assertEquals(
                marker,
                database.postestQueries
                    .selectAllEnvironmentVariables()
                    .executeAsOne()
                    .value_,
            )
            assertTrue(
                database.postestQueries
                    .selectWorkspaceSession()
                    .executeAsOne()
                    .contains(marker),
            )
            assertTrue(
                database.postestQueries
                    .selectAllHistory()
                    .executeAsOne()
                    .request_json
                    .contains(marker),
            )
            bootstrap.close()

            StorageBootstrap(directory, keys).open().use { reopened ->
                assertEquals(StorageMode.UNENCRYPTED, reopened.mode)
                val restored = PostestDatabase(reopened.driver)
                assertEquals(marker, CollectionRepository(restored, reopened.codec).loadCollections().single().name)
                assertEquals(
                    marker,
                    EnvironmentRepository(restored, reopened.codec)
                        .loadEnvironments()
                        .single()
                        .variables
                        .single()
                        .value,
                )
                assertEquals(request, HistoryRepository(restored, reopened.codec).loadHistory().single().request)
                assertEquals(session, SessionRepository(restored, reopened.codec).load())
                assertNull(reopened.codec.recoveryKey())
            }
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use {
                assertEquals(StorageMode.UNENCRYPTED, it.mode)
            }
            verify { keys wasNot Called }
        }

    @Test
    fun `completed plaintext staging is recovered with its saved mode`() =
        withDirectory { directory ->
            val keys = mockk<NativeKeyStore>()
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.UNENCRYPTED).use {
                CollectionRepository(PostestDatabase(it.driver), it.codec).createCollection("Saved", "")
            }
            Files.move(directory.resolve(StorageBootstrap.DATABASE_NAME), directory.resolve("posttest.db.pending"))
            StorageBootstrap(directory, keys).open().use {
                assertEquals(StorageMode.UNENCRYPTED, it.mode)
                assertEquals("Saved", CollectionRepository(PostestDatabase(it.driver), it.codec).loadCollections().single().name)
            }
            verify { keys wasNot Called }
        }

    @Test
    fun `encrypted mode persists and a missing key cannot be bypassed by selecting plaintext`() =
        withDirectory { directory ->
            val keys = mockk<NativeKeyStore>()
            var savedKey: ByteArray? = null
            every { keys.write(any(), any()) } answers { savedKey = secondArg<ByteArray>().copyOf() }
            every { keys.read(any()) } answers { savedKey?.copyOf() }
            StorageBootstrap(directory, keys).open(initialMode = StorageMode.ENCRYPTED).use {
                assertTrue(it.needsRecoveryAcknowledgement)
                assertTrue(it.codec.recoveryKey()!!.isNotBlank())
                it.acknowledgeRecoveryKey()
            }
            StorageBootstrap(directory, keys).open().use {
                assertEquals(StorageMode.ENCRYPTED, it.mode)
                assertFalse(it.needsRecoveryAcknowledgement)
            }
            savedKey = null
            val path = directory.resolve(StorageBootstrap.DATABASE_NAME)
            val before = Files.readAllBytes(path)
            val failure =
                assertFailsWith<StorageException> {
                    StorageBootstrap(directory, keys).open(initialMode = StorageMode.UNENCRYPTED)
                }
            assertEquals(StorageProblem.KEY_MISSING, failure.problem)
            assertTrue(before.contentEquals(Files.readAllBytes(path)))
            verify(exactly = 1) { keys.write(any(), any()) }
        }

    @Test
    fun `missing unsupported and conflicting format markers fail without replacing the database`() =
        withDirectory { directory ->
            val keys = mockk<NativeKeyStore>()
            val mutations =
                listOf(
                    "DROP TABLE PlainStorage",
                    "UPDATE PlainStorage SET version = 99",
                    "CREATE TABLE ProtectedStorage (id INTEGER)",
                )
            mutations.forEachIndexed { index, mutation ->
                val location = directory.resolve(index.toString())
                StorageBootstrap(location, keys).open(initialMode = StorageMode.UNENCRYPTED).close()
                val path = location.resolve(StorageBootstrap.DATABASE_NAME)
                DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
                    connection.createStatement().use { it.execute(mutation) }
                }
                val before = Files.readAllBytes(path)
                assertEquals(
                    StorageProblem.INVALID_DATA,
                    assertFailsWith<StorageException> {
                        StorageBootstrap(
                            location,
                            keys,
                        ).open(initialMode = StorageMode.UNENCRYPTED)
                    }.problem,
                )
                assertTrue(before.contentEquals(Files.readAllBytes(path)))
            }
            verify { keys wasNot Called }
        }

    private fun withDirectory(action: suspend (Path) -> Unit) =
        runBlocking {
            val root = Path.of("build", "storage-mode-tests")
            Files.createDirectories(root)
            val directory = Files.createTempDirectory(root, "workspace-")
            try {
                action(directory)
            } finally {
                Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
            }
        }
}
