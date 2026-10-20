package su.kidoz.postest.data.auth

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.parseQueryString
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import su.kidoz.postest.data.http.HttpClientFactory
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.OAuth2Failure
import su.kidoz.postest.domain.model.OAuth2GrantType
import java.net.URI
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OAuth2TokenProviderTest {
    private val tokenJson =
        """{"access_token":"token-1","token_type":"Bearer","expires_in":3600,"refresh_token":"refresh-1"}"""

    @Test
    fun `client credentials sends basic auth and parses the token`() =
        runBlocking {
            val requests = mutableListOf<Parameters>()
            val authHeaders = mutableListOf<String?>()
            val server =
                tokenServer { parameters, authorization ->
                    requests += parameters
                    authHeaders += authorization
                    tokenJson
                }
            val client = HttpClientFactory.create()
            try {
                val provider = KtorOAuth2TokenProvider(client)
                val token =
                    provider
                        .authorize(
                            AuthConfig.OAuth2(
                                grantType = OAuth2GrantType.CLIENT_CREDENTIALS,
                                tokenUrl = "http://127.0.0.1:${server.port}/token",
                                clientId = "client",
                                clientSecret = "secret",
                                scope = "read",
                            ),
                        ).getOrThrow()

                assertEquals("token-1", token.accessToken)
                assertEquals("refresh-1", token.refreshToken)
                assertTrue(token.expiresAt > System.currentTimeMillis())
                assertEquals("client_credentials", requests.single()["grant_type"])
                assertEquals("read", requests.single()["scope"])
                val expected = Base64.getEncoder().encodeToString("client:secret".toByteArray())
                assertEquals("Basic $expected", authHeaders.single())
            } finally {
                client.close()
                server.stop()
            }
        }

    @Test
    fun `authorization code uses pkce and the loopback redirect`() =
        runBlocking {
            var challenge: String? = null
            var exchangedVerifier: String? = null
            val server =
                tokenServer { parameters, _ ->
                    assertEquals("authorization_code", parameters["grant_type"])
                    assertEquals("test-code", parameters["code"])
                    exchangedVerifier = parameters["code_verifier"]
                    tokenJson
                }
            val client = HttpClientFactory.create()
            try {
                val provider =
                    KtorOAuth2TokenProvider(
                        client = client,
                        browser = { authorizationUrl ->
                            val parameters = parseQueryString(authorizationUrl.rawQuery)
                            challenge = parameters["code_challenge"]
                            val redirect = assertNotNull(parameters["redirect_uri"])
                            val callback = "$redirect?code=test-code&state=${parameters["state"]}"
                            URI(callback).toURL().openStream().use { it.readBytes() }
                        },
                    )
                val token =
                    provider
                        .authorize(
                            AuthConfig.OAuth2(
                                grantType = OAuth2GrantType.AUTHORIZATION_CODE,
                                authorizationUrl = "http://127.0.0.1:1/authorize",
                                tokenUrl = "http://127.0.0.1:${server.port}/token",
                                clientId = "client",
                                scope = "read",
                            ),
                        ).getOrThrow()

                assertEquals("token-1", token.accessToken)
                val verifier = assertNotNull(exchangedVerifier)
                assertEquals(challenge, codeChallenge(verifier))
            } finally {
                client.close()
                server.stop()
            }
        }

    @Test
    fun `expired stored tokens refresh once and are cached`() =
        runBlocking {
            var calls = 0
            val server =
                tokenServer { parameters, _ ->
                    calls++
                    assertEquals("refresh_token", parameters["grant_type"])
                    assertEquals("refresh-1", parameters["refresh_token"])
                    tokenJson
                }
            val client = HttpClientFactory.create()
            try {
                val provider = KtorOAuth2TokenProvider(client)
                val config =
                    AuthConfig.OAuth2(
                        tokenUrl = "http://127.0.0.1:${server.port}/token",
                        clientId = "client",
                        accessToken = "expired",
                        expiresAt = 1,
                        refreshToken = "refresh-1",
                    )

                val first = provider.token(config).getOrThrow()
                val second = provider.token(config).getOrThrow()

                assertEquals("token-1", first.accessToken)
                assertEquals(first, second)
                assertEquals(1, calls)
            } finally {
                client.close()
                server.stop()
            }
        }

    @Test
    fun `missing and expired tokens fail without a refresh path`() =
        runBlocking {
            val client = HttpClientFactory.create()
            try {
                val provider = KtorOAuth2TokenProvider(client)
                val missing = provider.token(AuthConfig.OAuth2()).exceptionOrNull()
                val expired =
                    provider
                        .token(AuthConfig.OAuth2(accessToken = "old", expiresAt = 1))
                        .exceptionOrNull()

                assertEquals(OAuth2Failure.AUTHORIZATION_REQUIRED, (missing as OAuth2Exception).failure)
                assertEquals(OAuth2Failure.TOKEN_EXPIRED, (expired as OAuth2Exception).failure)
            } finally {
                client.close()
            }
        }

    @Test
    fun `non-loopback http endpoints are rejected and pkce matches the RFC vector`() =
        runBlocking {
            val client = HttpClientFactory.create()
            try {
                val provider = KtorOAuth2TokenProvider(client)
                val failure =
                    assertFailsWith<OAuth2Exception> {
                        provider
                            .authorize(AuthConfig.OAuth2(tokenUrl = "http://example.test/token", clientId = "client"))
                            .getOrThrow()
                    }
                assertEquals(OAuth2Failure.INVALID_ENDPOINT, failure.failure)
                assertEquals(
                    "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                    codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
                )
            } finally {
                client.close()
            }
        }

    @Test
    fun `authorization url keeps existing parameters`() {
        val url =
            buildAuthorizationUrl(
                AuthConfig.OAuth2(
                    authorizationUrl = "https://provider.test/authorize?tenant=alpha",
                    clientId = "client",
                    scope = "read write",
                ),
                redirectUri = "http://127.0.0.1:8080/callback",
                state = "state-1",
                challenge = "challenge-1",
            )
        val parameters = parseQueryString(URI(url).rawQuery)
        assertEquals("alpha", parameters["tenant"])
        assertEquals("code", parameters["response_type"])
        assertEquals("client", parameters["client_id"])
        assertEquals("read write", parameters["scope"])
        assertEquals("state-1", parameters["state"])
        assertEquals("challenge-1", parameters["code_challenge"])
        assertEquals("S256", parameters["code_challenge_method"])
    }

    private class TestServer(
        val port: Int,
        private val stopServer: () -> Unit,
    ) {
        fun stop() = stopServer()
    }

    private fun tokenServer(handler: (Parameters, String?) -> String): TestServer {
        val server =
            embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("/token") {
                        val parameters = call.receiveParameters()
                        val body = handler(parameters, call.request.headers[HttpHeaders.Authorization])
                        call.respondText(body, ContentType.Application.Json)
                    }
                }
            }.start(wait = false)
        val port =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
        return TestServer(port) { server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000) }
    }
}
