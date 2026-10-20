package su.kidoz.postest.data.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.network.tls.CertificateAndKey
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

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

object HttpClientFactory {
    fun create(enableLogging: Boolean = false): HttpClient = buildClient(enableLogging = enableLogging)

    fun createWithClientCertificate(
        certAndKey: CertificateAndKey,
        enableLogging: Boolean = false,
    ): HttpClient = buildClient(certAndKey = certAndKey, enableLogging = enableLogging)

    private fun buildClient(
        certAndKey: CertificateAndKey? = null,
        enableLogging: Boolean = false,
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

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            engine {
                requestTimeout = 60_000
                if (certAndKey != null) {
                    https {
                        certificates += certAndKey
                    }
                }
            }

            expectSuccess = false
        }
}
