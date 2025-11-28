package su.kidoz.postest.data.http

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.http.HttpMethod as KtorHttpMethod

class RequestExecutorTest {
    private fun createMockClient(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler(handler)
            }
            install(ContentNegotiation) {
                json()
            }
        }

    @Test
    fun `execute GET request successfully`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals(KtorHttpMethod.Get, request.method)
                    assertEquals("https://api.example.com/users", request.url.toString())
                    respond(
                        content = """{"id": 1, "name": "John"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(200, response.statusCode)
            assertTrue(response.body.contains("John"))
        }

    @Test
    fun `execute POST request with JSON body`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals(KtorHttpMethod.Post, request.method)
                    respond(
                        content = """{"id": 1, "created": true}""",
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.POST,
                    url = "https://api.example.com/users",
                    body = RequestBody.Json("""{"name": "John"}"""),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            assertEquals(201, result.getOrNull()?.statusCode)
        }

    @Test
    fun `execute request with query parameters`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("page", request.url.parameters["page"])
                    assertEquals("10", request.url.parameters["limit"])
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    queryParams =
                        listOf(
                            KeyValue("page", "page", enabled = true),
                            KeyValue("limit", "10", enabled = true),
                        ),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with headers`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("application/json", request.headers["Accept"])
                    assertEquals("custom-value", request.headers["X-Custom-Header"])
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    headers =
                        listOf(
                            KeyValue("Accept", "application/json", enabled = true),
                            KeyValue("X-Custom-Header", "custom-value", enabled = true),
                        ),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with disabled headers should not include them`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals(null, request.headers["X-Disabled"])
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    headers =
                        listOf(
                            KeyValue("X-Disabled", "value", enabled = false),
                        ),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with Basic auth`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    assertTrue(authHeader?.startsWith("Basic ") == true)
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    auth = AuthConfig.Basic("user", "password"),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with Bearer token`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("Bearer my-token", request.headers[HttpHeaders.Authorization])
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    auth = AuthConfig.Bearer("my-token"),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with API key in header`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("my-api-key", request.headers["X-API-Key"])
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                    auth = AuthConfig.ApiKey("X-API-Key", "my-api-key", AuthConfig.ApiKey.AddTo.HEADER),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request with variable resolution`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("https://api.test.com/users", request.url.toString())
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "{{baseUrl}}/users",
                )
            val environment =
                Environment(
                    name = "Test",
                    variables = listOf(Variable(key = "baseUrl", value = "https://api.test.com")),
                )

            val result = executor.execute(request, environment)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute request handles server error`() =
        runTest {
            val mockClient =
                createMockClient {
                    respond(
                        content = """{"error": "Internal Server Error"}""",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            assertEquals(500, result.getOrNull()?.statusCode)
        }

    @Test
    fun `execute request handles network failure`() =
        runTest {
            val mockClient =
                createMockClient {
                    throw java.io.IOException("Network error")
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            val result = executor.execute(request)

            assertTrue(result.isFailure)
        }

    @Test
    fun `execute PUT request`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals(KtorHttpMethod.Put, request.method)
                    respond(
                        content = """{"updated": true}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.PUT,
                    url = "https://api.example.com/users/1",
                    body = RequestBody.Json("""{"name": "Updated"}"""),
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
        }

    @Test
    fun `execute DELETE request`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals(KtorHttpMethod.Delete, request.method)
                    respond(
                        content = "",
                        status = HttpStatusCode.NoContent,
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.DELETE,
                    url = "https://api.example.com/users/1",
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            assertEquals(204, result.getOrNull()?.statusCode)
        }

    @Test
    fun `response includes timing information`() =
        runTest {
            val mockClient =
                createMockClient {
                    respond(
                        content = "{}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertTrue(response.time.total >= 0)
        }

    @Test
    fun `response includes content size`() =
        runTest {
            val responseBody = """{"id": 1, "name": "John Doe"}"""
            val mockClient =
                createMockClient {
                    respond(
                        content = responseBody,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users/1",
                )

            val result = executor.execute(request)

            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(responseBody.toByteArray().size.toLong(), response.size)
        }
}
