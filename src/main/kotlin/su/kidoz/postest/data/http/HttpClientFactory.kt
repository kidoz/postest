package su.kidoz.postest.data.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.network.tls.CertificateAndKey
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import su.kidoz.postest.domain.model.CertificatePin
import javax.net.ssl.X509TrustManager

/** Header names that must not be forwarded when a redirect changes the request origin. */
internal val SensitiveRequestHeaders = AttributeKey<Set<String>>("PostestSensitiveRequestHeaders")

/** Original scheme/host/port of a request, kept across redirects. */
internal val OriginalRequestAuthority = AttributeKey<String>("PostestOriginalRequestAuthority")

internal fun URLBuilder.authorityKey(): String = "${protocol.name.lowercase()}://${host.lowercase()}:$port"

/**
 * Removes credentials from redirects that leave the original origin.
 * Ktor removes Authorization itself, but custom API-key headers are not known to it.
 */
internal fun HttpClientConfig<*>.installSensitiveHeaderGuard() {
    install(
        createClientPlugin("SensitiveHeaderGuard") {
            on(Send) { request ->
                val originalAuthority = request.attributes.getOrNull(OriginalRequestAuthority)
                if (originalAuthority != null && request.url.authorityKey() != originalAuthority) {
                    request.headers.remove(HttpHeaders.Authorization)
                    request.headers.remove(HttpHeaders.ProxyAuthorization)
                    request.headers.remove(HttpHeaders.Cookie)
                    request.attributes.getOrNull(SensitiveRequestHeaders)?.forEach { name ->
                        request.headers.remove(name)
                    }
                }
                proceed(request)
            }
        },
    )
}

internal fun isSensitiveHeaderName(name: String): Boolean {
    val normalized = name.lowercase()
    return normalized == "authorization" ||
        normalized == "proxy-authorization" ||
        normalized == "cookie" ||
        normalized == "set-cookie" ||
        normalized.contains("token") ||
        normalized.contains("secret") ||
        normalized.contains("api-key") ||
        normalized.contains("apikey") ||
        normalized.contains("auth")
}

/** Upper bound for a single WebSocket frame accepted from the server. */
internal const val MAX_WEBSOCKET_FRAME_BYTES = 10L * 1024 * 1024

object HttpClientFactory {
    fun create(enableLogging: Boolean = false): HttpClient = buildClient(enableLogging = enableLogging)

    fun createWithClientCertificate(
        certAndKey: CertificateAndKey,
        enableLogging: Boolean = false,
        trustManager: X509TrustManager? = null,
    ): HttpClient = buildClient(certAndKey = certAndKey, enableLogging = enableLogging, trustManager = trustManager)

    fun createWithServerPin(
        pin: CertificatePin,
        enableLogging: Boolean = false,
    ): HttpClient = createWithTls(trustManager = createPinningTrustManager(pin), enableLogging = enableLogging)

    fun createWithTls(
        certAndKey: CertificateAndKey? = null,
        trustManager: X509TrustManager? = null,
        enableLogging: Boolean = false,
    ): HttpClient = buildClient(certAndKey = certAndKey, enableLogging = enableLogging, trustManager = trustManager)

    private fun buildClient(
        certAndKey: CertificateAndKey? = null,
        enableLogging: Boolean = false,
        trustManager: X509TrustManager? = null,
    ): HttpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                    },
                )
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = if (enableLogging) LogLevel.HEADERS else LogLevel.NONE
                sanitizeHeader { header -> isSensitiveHeaderName(header) }
            }

            installSensitiveHeaderGuard()

            install(WebSockets) {
                maxFrameSize = MAX_WEBSOCKET_FRAME_BYTES
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            engine {
                requestTimeout = 60_000
                if (certAndKey != null || trustManager != null) {
                    https {
                        if (certAndKey != null) certificates += certAndKey
                        if (trustManager != null) this.trustManager = trustManager
                    }
                }
            }

            expectSuccess = false
        }
}
