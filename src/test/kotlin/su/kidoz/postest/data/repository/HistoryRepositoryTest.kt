package su.kidoz.postest.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.ResponseTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HistoryRepositoryTest {
    private lateinit var repository: HistoryRepository

    @BeforeTest
    fun setup() {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        repository = HistoryRepository(database)
    }

    @Test
    fun `loadHistory should return empty list when no history`() =
        runTest {
            val history = repository.loadHistory()

            assertTrue(history.isEmpty())
        }

    @Test
    fun `addHistoryEntry should add entry and reload history`() =
        runTest {
            val entry = createHistoryEntry()

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()

            assertEquals(1, history.size)
        }

    @Test
    fun `history flow should emit updates`() =
        runTest {
            val entry = createHistoryEntry()

            repository.addHistoryEntry(entry)
            val history = repository.history.first()

            assertEquals(1, history.size)
        }

    @Test
    fun `addHistoryEntry should store request correctly`() =
        runTest {
            val request =
                HttpRequest(
                    method = HttpMethod.POST,
                    url = "https://api.example.com/users",
                    name = "Create User",
                )
            val entry =
                HistoryEntry(
                    request = request,
                    response = null,
                    duration = 100,
                    timestamp = System.currentTimeMillis(),
                )

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()
            val savedEntry = history.first()

            assertEquals(HttpMethod.POST, savedEntry.request.method)
            assertEquals("https://api.example.com/users", savedEntry.request.url)
            assertEquals("Create User", savedEntry.request.name)
        }

    @Test
    fun `addHistoryEntry should store response correctly`() =
        runTest {
            val response =
                HttpResponse(
                    statusCode = 200,
                    statusText = "OK",
                    headers = mapOf("Content-Type" to listOf("application/json")),
                    body = """{"id": 1}""",
                    contentType = "application/json",
                    size = 10,
                    time = ResponseTime(total = 50),
                )
            val entry = createHistoryEntry(response = response)

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()
            val savedEntry = history.first()

            assertNotNull(savedEntry.response)
            assertEquals(200, savedEntry.response?.statusCode)
            assertEquals("OK", savedEntry.response?.statusText)
        }

    @Test
    fun `addHistoryEntry should handle null response`() =
        runTest {
            val entry = createHistoryEntry(response = null)

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()

            assertEquals(null, history.first().response)
        }

    @Test
    fun `deleteHistoryEntry should remove specific entry`() =
        runTest {
            val entry1 = createHistoryEntry()
            val entry2 = createHistoryEntry()

            repository.addHistoryEntry(entry1)
            repository.addHistoryEntry(entry2)
            repository.deleteHistoryEntry(entry1.id)
            val history = repository.loadHistory()

            assertEquals(1, history.size)
            assertEquals(entry2.id, history.first().id)
        }

    @Test
    fun `clearHistory should remove all entries`() =
        runTest {
            repository.addHistoryEntry(createHistoryEntry())
            repository.addHistoryEntry(createHistoryEntry())
            repository.addHistoryEntry(createHistoryEntry())

            repository.clearHistory()
            val history = repository.loadHistory()

            assertTrue(history.isEmpty())
        }

    @Test
    fun `clearHistory should update flow with empty list`() =
        runTest {
            repository.addHistoryEntry(createHistoryEntry())

            repository.clearHistory()
            val history = repository.history.first()

            assertTrue(history.isEmpty())
        }

    @Test
    fun `history entries should be ordered by timestamp descending`() =
        runTest {
            val oldEntry = createHistoryEntry(timestamp = 1000)
            val newEntry = createHistoryEntry(timestamp = 2000)

            repository.addHistoryEntry(oldEntry)
            repository.addHistoryEntry(newEntry)
            val history = repository.loadHistory()

            assertEquals(newEntry.id, history.first().id)
            assertEquals(oldEntry.id, history.last().id)
        }

    @Test
    fun `addHistoryEntry should store duration`() =
        runTest {
            val entry = createHistoryEntry(duration = 500)

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()

            assertEquals(500, history.first().duration)
        }

    @Test
    fun `addHistoryEntry should generate id if blank`() =
        runTest {
            val entry =
                HistoryEntry(
                    id = "",
                    request = HttpRequest(url = "https://example.com"),
                    response = null,
                    duration = 100,
                    timestamp = System.currentTimeMillis(),
                )

            repository.addHistoryEntry(entry)
            val history = repository.loadHistory()

            assertTrue(history.first().id.isNotBlank())
        }

    private fun createHistoryEntry(
        response: HttpResponse? =
            HttpResponse(
                statusCode = 200,
                statusText = "OK",
                headers = emptyMap(),
                body = "{}",
                contentType = "application/json",
                size = 2,
                time = ResponseTime(total = 100),
            ),
        duration: Long = 100,
        timestamp: Long = System.currentTimeMillis(),
    ) = HistoryEntry(
        request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com/test",
            ),
        response = response,
        duration = duration,
        timestamp = timestamp,
    )
}
