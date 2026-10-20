package su.kidoz.postest.data.repository

import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {
    @Test
    fun `fresh installations have no saved session`() =
        runTest {
            val repository = SessionRepository(TestDatabaseFactory.createInMemoryDatabase(), TestDatabaseFactory.cipher())
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
            val response =
                HttpResponse(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("Content-Type" to listOf("application/json")),
                    body = "{\"ok\":true}",
                    contentType = "application/json",
                    size = 11,
                    time = ResponseTime(total = 42),
                )
            val expected =
                DraftSession(
                    tabs =
                        listOf(
                            DraftTab(
                                "tab",
                                request,
                                true,
                                bodies,
                                response = response,
                                error = "previous failure",
                                webSocketMessages =
                                    listOf(
                                        WebSocketMessage(
                                            direction = WebSocketDirection.RECEIVED,
                                            content = "hello",
                                            timestamp = 123,
                                        ),
                                    ),
                            ),
                        ),
                    activeTabId = "tab",
                )
            SessionRepository(database, TestDatabaseFactory.cipher()).save(expected)
            assertEquals(expected, SessionRepository(database, TestDatabaseFactory.cipher()).load())
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
            val repository = SessionRepository(TestDatabaseFactory.createInMemoryDatabase(), TestDatabaseFactory.cipher())
            repository.save(DraftSession(tabs = listOf(DraftTab("old", HttpRequest(), true)), activeTabId = "old"))
            val latest = DraftSession(tabs = listOf(DraftTab("new", HttpRequest(), false)), activeTabId = "new")
            repository.save(latest)
            assertEquals(latest, repository.load())
        }

    @Test
    fun `unreadable and future sessions are retained before fallback saves`() =
        runTest {
            val database = TestDatabaseFactory.createInMemoryDatabase()
            val repository = SessionRepository(database, TestDatabaseFactory.cipher())
            listOf(
                "not valid JSON",
                """{"version":2,"tabs":[],"activeTabId":null}""",
                """{"version":1,"tabs":[],"activeTabId":null}""",
            ).forEach { stored ->
                database.postestQueries.saveWorkspaceSession(
                    TestDatabaseFactory.cipher().encode("WorkspaceSession", "current", "snapshot_json", stored),
                )
                val failure = assertFailsWith<IllegalStateException> { repository.load() }
                assertEquals("Saved drafts are invalid or unsupported", failure.message)
                val replacement = DraftSession(tabs = listOf(DraftTab("new", HttpRequest(), false)), activeTabId = "new")
                repository.save(replacement)
                assertEquals(
                    stored,
                    TestDatabaseFactory.cipher().decode(
                        "WorkspaceSession",
                        "backup",
                        "snapshot_json",
                        database.postestQueries.selectWorkspaceSessionBackup().executeAsOne(),
                    ),
                )
                assertEquals(replacement, repository.load())
            }
        }
}
