package su.kidoz.postest.data.http

import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CertificatePin
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.util.VariableResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KtorWebSocketTransportTest {
    @Test
    fun `normalizes websocket and http schemes and drops fragments`() {
        assertEquals("ws://example.test/socket", normalizeWebSocketUrl("ws://example.test/socket"))
        assertEquals("wss://example.test/socket", normalizeWebSocketUrl("wss://example.test/socket"))
        assertEquals("ws://example.test/socket", normalizeWebSocketUrl("http://example.test/socket"))
        assertEquals("wss://example.test/socket", normalizeWebSocketUrl("https://example.test/socket"))
        assertEquals("wss://example.test/socket", normalizeWebSocketUrl("https://example.test/socket#ignored"))
    }

    @Test
    fun `rejects blank unsupported or hostless urls`() {
        assertNull(normalizeWebSocketUrl(""))
        assertNull(normalizeWebSocketUrl("   "))
        assertNull(normalizeWebSocketUrl("ftp://example.test/socket"))
        assertNull(normalizeWebSocketUrl("ws:///socket"))
        assertNull(normalizeWebSocketUrl("example.test/socket"))
    }

    @Test
    fun `resolves variables and appends enabled query parameters exactly once`() {
        val request =
            HttpRequest(
                url = "{{base}}/socket#fragment",
                queryParams =
                    listOf(
                        KeyValue("tenant", "a b"),
                        KeyValue("tag", "a&b"),
                        KeyValue("disabled", "ignored", enabled = false),
                        KeyValue("empty", ""),
                    ),
            )

        val url =
            buildWebSocketUrl(request) { value ->
                value.replace("{{base}}", "wss://example.test")
            }

        assertEquals(
            "wss://example.test/socket?tenant=a%20b&tag=a%26b&empty=",
            url,
        )
    }

    @Test
    fun `api key in query is appended to the websocket url`() {
        val request =
            HttpRequest(
                url = "wss://example.test/socket",
                auth = AuthConfig.ApiKey("key", "secret value", AuthConfig.ApiKey.AddTo.QUERY_PARAM),
            )

        assertEquals(
            "wss://example.test/socket?key=secret%20value",
            buildWebSocketUrl(request) { it },
        )
    }

    @Test
    fun `header and auth injection are rejected`() {
        val newline = HttpRequest(url = "wss://example.test", headers = listOf(KeyValue("X-Test", "a\r\nInjected: yes")))
        val controlName = HttpRequest(url = "wss://example.test", headers = listOf(KeyValue("X\u0001Test", "value")))
        val token = HttpRequest(url = "wss://example.test", auth = AuthConfig.Bearer("line1\nline2"))
        val safe = HttpRequest(url = "wss://example.test", headers = listOf(KeyValue("X-Test", "value")))

        assertEquals(true, hasHeaderInjection(newline) { it })
        assertEquals(true, hasHeaderInjection(controlName) { it })
        assertEquals(true, hasHeaderInjection(token) { it })
        assertEquals(false, hasHeaderInjection(safe) { it })
    }

    @Test
    fun `invalid certificate pins are rejected before connecting`() =
        runTest {
            val client = HttpClientFactory.create()
            try {
                val result =
                    KtorWebSocketTransport(client, VariableResolver())
                        .connect(
                            HttpRequest(
                                protocol = RequestProtocol.WEBSOCKET,
                                url = "wss://example.test/socket",
                                certificatePin = CertificatePin(sha256 = "bad"),
                            ),
                            ExecutionContext(),
                        )

                assertEquals("Certificate pin is invalid", result.exceptionOrNull()?.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun `certificate pins require a secure websocket url`() =
        runTest {
            val client = HttpClientFactory.create()
            try {
                val result =
                    KtorWebSocketTransport(client, VariableResolver())
                        .connect(
                            HttpRequest(
                                protocol = RequestProtocol.WEBSOCKET,
                                url = "ws://example.test/socket",
                                certificatePin = CertificatePin(sha256 = "0".repeat(64)),
                            ),
                            ExecutionContext(),
                        )

                assertEquals("Certificate pin is invalid", result.exceptionOrNull()?.message)
            } finally {
                client.close()
            }
        }

    @Test
    fun `invalid urls produce no websocket url`() {
        assertNull(buildWebSocketUrl(HttpRequest(url = "https:///missing-host")) { it })
        assertNull(buildWebSocketUrl(HttpRequest(url = "")) { it })
    }
}
