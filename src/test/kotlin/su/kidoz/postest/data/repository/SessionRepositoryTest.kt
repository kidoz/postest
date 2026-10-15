package su.kidoz.postest.data.repository

import kotlinx.coroutines.test.runTest
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.di.migrateWorkspaceSession
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {
    @Test
    fun `fresh installations have no saved session`() =
        runTest {
            val repository = SessionRepository(TestDatabaseFactory.createInMemoryDatabase())
            assertNull(repository.load())
        }

    @Test
    fun `a new repository restores requests and every body draft from storage`() =
        runTest {
            val database = TestDatabaseFactory.createInMemoryDatabase()
            val request = HttpRequest(name = "Draft", auth = AuthConfig.Basic("example", "example"))
            val bodies =
                listOf(
                    RequestBody.Json("{\"ok\":true}"),
                    RequestBody.Xml("<ok/>"),
                    RequestBody.Raw("raw", "text/custom"),
                    RequestBody.FormData(listOf(FormField("file", "/tmp/example.txt", FormFieldType.FILE))),
                    RequestBody.FormUrlEncoded(emptyList()),
                    RequestBody.Binary("/tmp/example.bin"),
                    RequestBody.None,
                )
            val expected = DraftSession(tabs = listOf(DraftTab("tab", request, true, bodies)), activeTabId = "tab")
            SessionRepository(database).save(expected)
            assertEquals(expected, SessionRepository(database).load())
            assertTrue(
                database.postestQueries
                    .selectAllHistory()
                    .executeAsList()
                    .isEmpty(),
            )
        }

    @Test
    fun `saving a new workspace replaces its closed tabs and active selection`() =
        runTest {
            val repository = SessionRepository(TestDatabaseFactory.createInMemoryDatabase())
            repository.save(DraftSession(tabs = listOf(DraftTab("old", HttpRequest(), true)), activeTabId = "old"))
            val latest = DraftSession(tabs = listOf(DraftTab("new", HttpRequest(), false)), activeTabId = "new")
            repository.save(latest)
            assertEquals(latest, repository.load())
        }

    @Test
    fun `unreadable and future sessions are retained before fallback saves`() =
        runTest {
            val database = TestDatabaseFactory.createInMemoryDatabase()
            val repository = SessionRepository(database)
            listOf(
                "not valid JSON",
                """{"version":2,"tabs":[],"activeTabId":null}""",
                """{"version":1,"tabs":[],"activeTabId":null}""",
            ).forEach { stored ->
                database.postestQueries.saveWorkspaceSession(stored)
                val failure = assertFailsWith<IllegalStateException> { repository.load() }
                assertEquals("Saved drafts are invalid or unsupported", failure.message)
                val replacement = DraftSession(tabs = listOf(DraftTab("new", HttpRequest(), false)), activeTabId = "new")
                repository.save(replacement)
                assertEquals(stored, database.postestQueries.selectWorkspaceSessionBackup().executeAsOne())
                assertEquals(replacement, repository.load())
            }
        }

    @Test
    fun `migration adds the session table to an existing database and is repeatable`() =
        runTest {
            val driver = TestDatabaseFactory.createInMemoryDriver()
            try {
                driver.execute(null, "CREATE TABLE Collection (id TEXT PRIMARY KEY)", 0)
                migrateWorkspaceSession(driver)
                migrateWorkspaceSession(driver)
                val database = PostestDatabase(driver)
                val repository = SessionRepository(database)
                val session = DraftSession(tabs = listOf(DraftTab("restored", HttpRequest(), true)), activeTabId = "restored")
                repository.save(session)
                assertEquals(session, repository.load())
            } finally {
                driver.close()
            }
        }
}
