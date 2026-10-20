package su.kidoz.postest.data.http

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import su.kidoz.postest.domain.model.*
import su.kidoz.postest.domain.port.OAuth2TokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
    fun `transport adapter maps engine failures to domain values without diagnostic content`() =
        runTest {
            val client = createMockClient { throw java.io.IOException("synthetic diagnostic content") }
            try {
                val outcome =
                    RequestExecutor(client).executeRequest(
                        HttpRequest(url = "https://example.test"),
                        ExecutionContext(),
                    )
                assertEquals(RequestOutcome.Failure(RequestFailure.NETWORK), outcome)
            } finally {
                client.close()
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
    fun `execute request with client certificate attempts cert loading`() =
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
                    clientCertificate = ClientCertConfig("/nonexistent/cert.p12", "", "pass"),
                )

            // Fails because cert file doesn't exist — verifies cert is resolved from clientCertificate field
            val result = executor.execute(request)

            assertTrue(result.isFailure)
            assertEquals("Client certificate could not be loaded", result.exceptionOrNull()?.message)
        }

    @Test
    fun `client certificate is independent of HTTP auth`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    // API Key header should be present even with cert config
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
                    clientCertificate = ClientCertConfig("/nonexistent/cert.p12"),
                )

            // Cert loading will fail, but this verifies auth + cert are independent fields
            val result = executor.execute(request)
            assertTrue(result.isFailure)
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
    fun `execute request propagates cancellation instead of returning failure`() =
        runTest {
            val mockClient =
                createMockClient {
                    throw CancellationException("Request cancelled")
                }

            val executor = RequestExecutor(mockClient)
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            // A cancelled request must rethrow CancellationException, not produce a Result.failure
            assertFailsWith<CancellationException> {
                executor.execute(request)
            }
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

    @Test
    fun `API key header is removed when a redirect changes host`() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val client =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            requests += request
                            if (request.url.host == "api.example.com") {
                                respond(
                                    content = "",
                                    status = HttpStatusCode.Found,
                                    headers = headersOf(HttpHeaders.Location, "https://evil.example.net/collect"),
                                )
                            } else {
                                respond(content = "ok", status = HttpStatusCode.OK)
                            }
                        }
                    }
                    installSensitiveHeaderGuard()
                }

            val executor = RequestExecutor(client)
            val result =
                executor.execute(
                    HttpRequest(
                        url = "https://api.example.com/start",
                        auth = AuthConfig.ApiKey("X-API-Key", "secret", AuthConfig.ApiKey.AddTo.HEADER),
                    ),
                )

            assertTrue(result.isSuccess)
            assertEquals(2, requests.size)
            assertEquals("secret", requests.first().headers["X-API-Key"])
            assertNull(requests.last().headers["X-API-Key"])
        }

    @Test
    fun `responses are truncated at the byte limit`() =
        runTest {
            val oversized = ByteArray(10 * 1024 * 1024 + 10) { 'a'.code.toByte() }
            val mockClient = createMockClient { respond(content = oversized) }

            val result = RequestExecutor(mockClient).execute(HttpRequest(url = "https://api.example.com/large"))

            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertTrue(response.truncated)
            assertEquals(10 * 1024 * 1024L, response.size)
            assertEquals(10 * 1024 * 1024, response.body.length)
        }

    @Test
    fun `oversized streaming responses finish without waiting for EOF`() =
        runTest {
            val channel = ByteChannel(autoFlush = true)
            val producer =
                backgroundScope.launch {
                    channel.writeFully(ByteArray(10 * 1024 * 1024 + 1) { 'a'.code.toByte() })
                    // Leave the stream open: a buffered request would wait indefinitely for EOF.
                }
            val client = createMockClient { respond(content = channel) }
            try {
                val response =
                    withContext(Dispatchers.Default) {
                        // Ktor's engine runs outside the test scheduler; use a real deadline.
                        withTimeout(5_000) {
                            RequestExecutor(client).execute(HttpRequest(url = "https://example.test/stream")).getOrThrow()
                        }
                    }
                assertTrue(response.truncated)
                assertEquals(10 * 1024 * 1024L, response.size)
                assertTrue(channel.isClosedForRead)
            } finally {
                channel.cancel(null)
                producer.cancel()
                client.close()
            }
        }

    @Test
    fun `explicit Content-Type header wins over the body default`() =
        runTest {
            var contentType: String? = null
            val mockClient =
                createMockClient { request ->
                    // Ktor carries Content-Type on the outgoing content rather than in headers
                    contentType = request.body.contentType?.toString()
                    respond(content = "{}", status = HttpStatusCode.OK)
                }

            val request =
                HttpRequest(
                    method = HttpMethod.PATCH,
                    url = "https://api.example.com/users/1",
                    headers = listOf(KeyValue("Content-Type", "application/merge-patch+json")),
                    body = RequestBody.Json("{}"),
                )

            val result = RequestExecutor(mockClient).execute(request)

            assertTrue(result.isSuccess)
            assertEquals("application/merge-patch+json", contentType)
        }

    @Test
    fun `query parameters are inserted before the fragment`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("existing", request.url.parameters["existing"])
                    assertEquals("new", request.url.parameters["added"])
                    assertEquals("section", request.url.fragment)
                    respond(content = "{}", status = HttpStatusCode.OK)
                }

            val request =
                HttpRequest(
                    url = "https://api.example.com/users?existing=existing#section",
                    queryParams = listOf(KeyValue("added", "new")),
                )

            assertTrue(RequestExecutor(mockClient).execute(request).isSuccess)
        }

    @Test
    fun `collection variables are resolved when no environment variable matches`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("collection.example.com", request.url.host)
                    respond(content = "{}", status = HttpStatusCode.OK)
                }

            val result =
                RequestExecutor(mockClient).execute(
                    HttpRequest(url = "https://{{host}}/users"),
                    collectionVariables = listOf(Variable(key = "host", value = "collection.example.com")),
                )

            assertTrue(result.isSuccess)
        }

    @Test
    fun `environment variables win over collection variables`() =
        runTest {
            val mockClient =
                createMockClient { request ->
                    assertEquals("environment.example.com", request.url.host)
                    respond(content = "{}", status = HttpStatusCode.OK)
                }

            val result =
                RequestExecutor(mockClient).execute(
                    HttpRequest(url = "https://{{host}}/users"),
                    environment =
                        Environment(
                            name = "Test",
                            variables = listOf(Variable(key = "host", value = "environment.example.com")),
                        ),
                    collectionVariables = listOf(Variable(key = "host", value = "collection.example.com")),
                )

            assertTrue(result.isSuccess)
        }

    @Test
    fun `OAuth2 access token is applied as a bearer header`() =
        runTest {
            var authorization: String? = null
            val mockClient =
                createMockClient { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    respond(content = "{}", status = HttpStatusCode.OK)
                }
            val request =
                HttpRequest(
                    url = "https://api.example.com/users",
                    auth = AuthConfig.OAuth2(accessToken = "token-1", tokenType = "Bearer"),
                )

            assertTrue(RequestExecutor(mockClient).execute(request).isSuccess)
            assertEquals("Bearer token-1", authorization)
        }

    @Test
    fun `missing or expired OAuth2 tokens fail with fixed messages`() =
        runTest {
            val executor = RequestExecutor(createMockClient { respond(content = "") })

            val missing = executor.execute(HttpRequest(url = "https://api.example.com", auth = AuthConfig.OAuth2()))
            val expired =
                executor.execute(
                    HttpRequest(
                        url = "https://api.example.com",
                        auth = AuthConfig.OAuth2(accessToken = "old", expiresAt = 1),
                    ),
                )

            assertEquals("OAuth 2.0 authorization is required", missing.exceptionOrNull()?.message)
            assertEquals("OAuth 2.0 token has expired. Authorize again", expired.exceptionOrNull()?.message)
        }

    @Test
    fun `OAuth2 provider refreshes and supplies the request token`() =
        runTest {
            var authorization: String? = null
            val mockClient =
                createMockClient { request ->
                    authorization = request.headers[HttpHeaders.Authorization]
                    respond(content = "{}", status = HttpStatusCode.OK)
                }
            val provider =
                object : OAuth2TokenProvider {
                    override suspend fun authorize(config: AuthConfig.OAuth2) = Result.success(OAuth2Token("unused"))

                    override suspend fun token(config: AuthConfig.OAuth2) = Result.success(OAuth2Token("refreshed-token"))
                }
            val request =
                HttpRequest(
                    url = "https://api.example.com/users",
                    auth = AuthConfig.OAuth2(tokenUrl = "https://provider.test/token", refreshToken = "refresh"),
                )

            assertTrue(RequestExecutor(mockClient, oauth2TokenProvider = provider).execute(request).isSuccess)
            assertEquals("Bearer refreshed-token", authorization)
        }

    @Test
    fun `OAuth2 tokens containing header injection are rejected`() =
        runTest {
            val provider =
                object : OAuth2TokenProvider {
                    override suspend fun authorize(config: AuthConfig.OAuth2) = Result.success(OAuth2Token("unused"))

                    override suspend fun token(config: AuthConfig.OAuth2) = Result.success(OAuth2Token("line1\r\nInjected: yes"))
                }
            val result =
                RequestExecutor(createMockClient { respond(content = "") }, oauth2TokenProvider = provider)
                    .execute(
                        HttpRequest(
                            url = "https://api.example.com",
                            auth = AuthConfig.OAuth2(tokenUrl = "https://provider.test/token", refreshToken = "refresh"),
                        ),
                    )

            assertEquals("OAuth 2.0 token request failed", result.exceptionOrNull()?.message)
        }

    @Test
    fun `invalid URL errors do not include the resolved URL`() =
        runTest {
            val executor = RequestExecutor(createMockClient { respond(content = "") })
            val result =
                executor.execute(
                    HttpRequest(url = "http://exa mple.test/{{secret}}"),
                    environment =
                        Environment(
                            name = "Test",
                            variables = listOf(Variable(key = "secret", value = "top-secret-value")),
                        ),
                )

            assertTrue(result.isFailure)
            assertEquals("Invalid URL", result.exceptionOrNull()?.message)
        }

    @Test
    fun `binary body with no selected file fails safely`() =
        runTest {
            val result =
                RequestExecutor(createMockClient { respond(content = "") }).execute(
                    HttpRequest(url = "https://api.example.com/upload", body = RequestBody.Binary("")),
                )

            assertTrue(result.isFailure)
            assertEquals("Select a file for the binary body", result.exceptionOrNull()?.message)
        }
}
