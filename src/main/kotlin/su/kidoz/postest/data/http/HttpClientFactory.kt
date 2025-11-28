package su.kidoz.postest.data.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientFactory {
    fun create(enableLogging: Boolean = false): HttpClient =
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
                sanitizeHeader { header ->
                    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                        header.contains("token", ignoreCase = true) ||
                        header.contains("secret", ignoreCase = true) ||
                        header.equals(HttpHeaders.Cookie, ignoreCase = true)
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }

            engine {
                requestTimeout = 60_000
            }

            expectSuccess = false
        }
}
