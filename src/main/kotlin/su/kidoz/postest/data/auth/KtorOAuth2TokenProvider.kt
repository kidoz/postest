package su.kidoz.postest.data.auth

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.OAuth2Failure
import su.kidoz.postest.domain.model.OAuth2GrantType
import su.kidoz.postest.domain.model.OAuth2Token
import su.kidoz.postest.domain.port.OAuth2TokenProvider
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private const val EXPIRY_SKEW_MILLIS = 30_000L

/**
 * Ktor-backed OAuth 2.0 token provider.
 * Authorization Code uses the system browser and an RFC 8252 loopback redirect.
 */
class KtorOAuth2TokenProvider(
    private val client: HttpClient,
    private val browser: (URI) -> Unit = ::openSystemBrowser,
    private val authorizationTimeoutMillis: Long = 300_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : OAuth2TokenProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()
    private val refreshedTokens = ConcurrentHashMap<String, OAuth2Token>()
    private val refreshMutex = Mutex()

    override suspend fun authorize(config: AuthConfig.OAuth2): Result<OAuth2Token> =
        oauthResult {
            validateEndpoint(config.tokenUrl)
            when (config.grantType) {
                OAuth2GrantType.CLIENT_CREDENTIALS -> {
                    requestToken(config, clientCredentialsForm(config))
                }

                OAuth2GrantType.AUTHORIZATION_CODE -> {
                    validateEndpoint(config.authorizationUrl)
                    authorizationCodeToken(config)
                }
            }
        }

    override suspend fun token(config: AuthConfig.OAuth2): Result<OAuth2Token> =
        oauthResult {
            storedToken(config)?.let { return@oauthResult it }
            if (config.refreshToken.isBlank()) {
                throw OAuth2Exception(
                    if (config.accessToken.isBlank()) OAuth2Failure.AUTHORIZATION_REQUIRED else OAuth2Failure.TOKEN_EXPIRED,
                )
            }
            refreshMutex.withLock {
                refreshedTokens[cacheKey(config)]?.takeIf { it.isUsable() }?.let { return@withLock it }
                validateEndpoint(config.tokenUrl)
                requestToken(config, refreshForm(config)).also { refreshedTokens[cacheKey(config)] = it }
            }
        }

    private fun storedToken(config: AuthConfig.OAuth2): OAuth2Token? {
        if (config.accessToken.isBlank()) return null
        if (config.expiresAt > 0 && clock() >= config.expiresAt - EXPIRY_SKEW_MILLIS) return null
        return OAuth2Token(
            accessToken = config.accessToken,
            tokenType = config.tokenType,
            refreshToken = config.refreshToken,
            expiresAt = config.expiresAt,
            scope = config.scope,
        )
    }

    private fun OAuth2Token.isUsable(): Boolean = expiresAt <= 0 || clock() < expiresAt - EXPIRY_SKEW_MILLIS

    private fun cacheKey(config: AuthConfig.OAuth2): String = "${config.tokenUrl}\u0000${config.clientId}\u0000${config.refreshToken}"

    private suspend fun authorizationCodeToken(config: AuthConfig.OAuth2): OAuth2Token {
        val verifier = codeVerifier()
        val challenge = codeChallenge(verifier)
        val state = randomToken()
        val server =
            try {
                HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            } catch (_: Exception) {
                throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
            }
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "oauth2-callback").apply { isDaemon = true } }
        server.executor = executor
        val authorizationCode = CompletableDeferred<String>()
        server.createContext("/callback") { exchange ->
            handleCallback(exchange, state, authorizationCode)
        }
        server.start()
        return try {
            val redirectUri = "http://127.0.0.1:${server.address.port}/callback"
            val authorizationUrl = buildAuthorizationUrl(config, redirectUri, state, challenge)
            try {
                withContext(Dispatchers.IO) { browser(URI(authorizationUrl)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw OAuth2Exception(OAuth2Failure.BROWSER_UNAVAILABLE)
            }
            val code =
                try {
                    withTimeout(authorizationTimeoutMillis) { authorizationCode.await() }
                } catch (_: TimeoutCancellationException) {
                    throw OAuth2Exception(OAuth2Failure.TIMED_OUT)
                }
            requestToken(
                config,
                tokenForm(
                    config,
                    "authorization_code",
                    listOf("code" to code, "redirect_uri" to redirectUri, "code_verifier" to verifier),
                ),
            )
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun handleCallback(
        exchange: HttpExchange,
        expectedState: String,
        result: CompletableDeferred<String>,
    ) {
        val parameters = parseQueryString(exchange.requestURI.rawQuery.orEmpty())
        val code = parameters["code"]
        val error = parameters["error"]
        val state = parameters["state"]
        if (state == expectedState && error == null && !code.isNullOrBlank()) {
            result.complete(code)
            respond(exchange, 200, "Authorization complete. You can close this window.")
        } else {
            result.completeExceptionally(OAuth2Exception(OAuth2Failure.REJECTED))
            respond(exchange, 400, "Authorization failed. You can close this window.")
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        message: String,
    ) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Suppress("ThrowsCount")
    private suspend fun requestToken(
        config: AuthConfig.OAuth2,
        form: Parameters,
    ): OAuth2Token {
        val response =
            try {
                client.post(config.tokenUrl) {
                    // Ktor does not follow POST redirects by default, so client secrets are not replayed.
                    contentType(ContentType.Application.FormUrlEncoded)
                    if (!config.clientSecretInBody && config.clientSecret.isNotBlank()) {
                        header(HttpHeaders.Authorization, basicCredentials(config))
                    }
                    setBody(FormDataContent(form))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
            }
        val body =
            try {
                response.bodyAsText()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
            }
        if (!response.status.isSuccess()) throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
        val token =
            runCatching { json.decodeFromString<TokenResponse>(body) }
                .getOrElse { throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED) }
        if (token.accessToken.isBlank()) throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
        return OAuth2Token(
            accessToken = token.accessToken,
            tokenType = token.tokenType?.takeIf { it.isNotBlank() } ?: "Bearer",
            refreshToken = token.refreshToken.orEmpty(),
            expiresAt = token.expiresIn?.let { clock() + it * 1_000L } ?: 0L,
            scope = token.scope ?: config.scope,
        )
    }

    private fun clientCredentialsForm(config: AuthConfig.OAuth2): Parameters = tokenForm(config, "client_credentials", emptyList())

    private fun refreshForm(config: AuthConfig.OAuth2): Parameters =
        tokenForm(config, "refresh_token", listOf("refresh_token" to config.refreshToken))

    private fun tokenForm(
        config: AuthConfig.OAuth2,
        grantType: String,
        extra: List<Pair<String, String>>,
    ): Parameters =
        Parameters.build {
            append("grant_type", grantType)
            append("client_id", config.clientId)
            if (config.clientSecretInBody && config.clientSecret.isNotBlank()) {
                append("client_secret", config.clientSecret)
            }
            if (config.scope.isNotBlank()) append("scope", config.scope)
            extra.forEach { (key, value) -> append(key, value) }
        }

    private fun basicCredentials(config: AuthConfig.OAuth2): String {
        val raw = "${config.clientId}:${config.clientSecret}".toByteArray(Charsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }

    private fun validateEndpoint(raw: String) {
        if (raw.isBlank()) throw OAuth2Exception(OAuth2Failure.INVALID_ENDPOINT)
        val uri = runCatching { URI(raw) }.getOrNull() ?: throw OAuth2Exception(OAuth2Failure.INVALID_ENDPOINT)
        val scheme = uri.scheme?.lowercase()
        val loopback = uri.host?.lowercase() in setOf("127.0.0.1", "localhost", "::1")
        val allowed = scheme == "https" || (scheme == "http" && loopback)
        if (!allowed || uri.host.isNullOrBlank()) throw OAuth2Exception(OAuth2Failure.INVALID_ENDPOINT)
    }

    private fun codeVerifier(): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private suspend fun <T> oauthResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OAuth2Exception) {
            Result.failure(error)
        } catch (_: Exception) {
            Result.failure(OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED))
        }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val scope: String? = null,
    )
}

internal fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

internal fun buildAuthorizationUrl(
    config: AuthConfig.OAuth2,
    redirectUri: String,
    state: String,
    challenge: String,
): String =
    URLBuilder(config.authorizationUrl)
        .apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", redirectUri)
            if (config.scope.isNotBlank()) parameters.append("scope", config.scope)
            parameters.append("state", state)
            parameters.append("code_challenge", challenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()

internal fun openSystemBrowser(uri: URI) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        throw OAuth2Exception(OAuth2Failure.BROWSER_UNAVAILABLE)
    }
    Desktop.getDesktop().browse(uri)
}
