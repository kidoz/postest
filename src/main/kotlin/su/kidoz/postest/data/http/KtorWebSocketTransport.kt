package su.kidoz.postest.data.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestFailureException
import su.kidoz.postest.domain.port.WebSocketConnection
import su.kidoz.postest.domain.port.WebSocketEvent
import su.kidoz.postest.domain.port.WebSocketTransport
import su.kidoz.postest.util.VariableResolver
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.Base64
import javax.net.ssl.SSLException

/** Ktor-backed WebSocket transport. Resolves variables and headers the same way HTTP requests do. */
class KtorWebSocketTransport(
    private val client: HttpClient,
    private val variableResolver: VariableResolver,
) : WebSocketTransport {
    override suspend fun connect(
        request: HttpRequest,
        context: ExecutionContext,
    ): Result<WebSocketConnection> {
        fun resolve(value: String): String = variableResolver.resolve(value, context.environment, context.collectionVariables)

        val url =
            buildWebSocketUrl(request, ::resolve)
                ?: return Result.failure(RequestFailureException(RequestFailure.INVALID_REQUEST))
        if (hasHeaderInjection(request, ::resolve)) {
            return Result.failure(RequestFailureException(RequestFailure.INVALID_REQUEST))
        }

        return try {
            val session =
                client.webSocketSession {
                    url(url)
                    request.headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header(resolve(it.key), resolve(it.value)) }
                    applyAuth(request.auth, ::resolve) { key, value -> header(key, value) }
                }
            Result.success(KtorWebSocketConnection(session))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(RequestFailureException(webSocketFailure(error)))
        }
    }

    private fun applyAuth(
        auth: AuthConfig?,
        resolve: (String) -> String,
        setHeader: (String, String) -> Unit,
    ) {
        when (auth) {
            is AuthConfig.Basic -> {
                val credentials =
                    Base64.getEncoder().encodeToString(
                        "${resolve(auth.username)}:${resolve(auth.password)}".toByteArray(),
                    )
                setHeader(HttpHeaders.Authorization, "Basic $credentials")
            }

            is AuthConfig.Bearer -> {
                setHeader(HttpHeaders.Authorization, "Bearer ${resolve(auth.token)}")
            }

            is AuthConfig.ApiKey -> {
                if (auth.addTo == AuthConfig.ApiKey.AddTo.HEADER) {
                    setHeader(resolve(auth.key), resolve(auth.value))
                }
            }

            AuthConfig.None, null -> {}
        }
    }
}

/** Rejects header names with control characters and values with CR, LF, or null bytes. */
internal fun hasHeaderInjection(
    request: HttpRequest,
    resolve: (String) -> String,
): Boolean {
    fun badName(name: String): Boolean = name.any { it.isISOControl() }

    fun badValue(value: String): Boolean = value.any { it == '\r' || it == '\n' || it == '\u0000' }

    val headerInjection =
        request.headers
            .filter { it.enabled && it.key.isNotBlank() }
            .any { badName(resolve(it.key)) || badValue(resolve(it.value)) }
    if (headerInjection) return true

    return when (val auth = request.auth) {
        is AuthConfig.Basic -> badValue(resolve(auth.username)) || badValue(resolve(auth.password))
        is AuthConfig.Bearer -> badValue(resolve(auth.token))
        is AuthConfig.ApiKey -> badName(resolve(auth.key)) || badValue(resolve(auth.value))
        AuthConfig.None, null -> false
    }
}

internal fun webSocketFailure(error: Throwable): RequestFailure =
    when (error) {
        is UnknownHostException -> RequestFailure.HOST_NOT_FOUND
        is ConnectException -> RequestFailure.CONNECTION_FAILED
        is HttpRequestTimeoutException, is SocketTimeoutException -> RequestFailure.TIMEOUT
        is SSLException -> RequestFailure.TLS
        is IllegalArgumentException -> RequestFailure.INVALID_REQUEST
        else -> RequestFailure.NETWORK
    }

/**
 * Converts an HTTP(S) or WS(S) URL into a WebSocket URL, dropping any fragment.
 * Returns null for blank input, unsupported schemes, or a missing host.
 */
internal fun normalizeWebSocketUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val normalizedScheme =
        when (scheme) {
            "ws", "wss" -> scheme
            "http" -> "ws"
            "https" -> "wss"
            else -> return null
        }
    if (uri.host.isNullOrBlank()) return null
    return "$normalizedScheme://${trimmed.substringBefore('#').substringAfter("://")}"
}

/** Resolves the URL and enabled query parameters, including an API key added to the query string. */
internal fun buildWebSocketUrl(
    request: HttpRequest,
    resolve: (String) -> String,
): String? {
    val base = normalizeWebSocketUrl(resolve(request.url)) ?: return null
    val parameters =
        request.queryParams
            .filter { it.enabled && it.key.isNotBlank() }
            .map { resolve(it.key) to resolve(it.value) }
            .toMutableList()
    val apiKey = request.auth as? AuthConfig.ApiKey
    if (apiKey?.addTo == AuthConfig.ApiKey.AddTo.QUERY_PARAM) {
        parameters += resolve(apiKey.key) to resolve(apiKey.value)
    }
    if (parameters.isEmpty()) return base
    val query =
        parameters.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value.encodeURLParameter()}"
        }
    val separator =
        when {
            base.endsWith("?") || base.endsWith("&") -> ""
            base.contains("?") -> "&"
            else -> "?"
        }
    return base + separator + query
}

private class KtorWebSocketConnection(
    private val session: DefaultClientWebSocketSession,
) : WebSocketConnection {
    private val sendMutex = Mutex()

    override val events: Flow<WebSocketEvent> =
        session.incoming.consumeAsFlow().mapNotNull { frame ->
            when (frame) {
                is Frame.Text -> WebSocketEvent.Text(frame.readText())
                is Frame.Binary -> WebSocketEvent.Binary(frame.data.size)
                is Frame.Close -> WebSocketEvent.Closed(frame.readReason()?.message)
                else -> null
            }
        }

    override suspend fun send(text: String) {
        sendMutex.withLock { session.send(Frame.Text(text)) }
    }

    override suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Closed by user"))
    }

    override fun abort() {
        session.cancel(CancellationException("WebSocket closed"))
    }
}
