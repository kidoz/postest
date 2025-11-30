package su.kidoz.postest.util

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

/**
 * Information about an available update.
 */
@Serializable
data class UpdateInfo(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("name")
    val name: String,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("body")
    val body: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("assets")
    val assets: List<ReleaseAsset> = emptyList(),
) {
    val version: String
        get() = tagName.removePrefix("v")

    val downloadUrl: String?
        get() = assets.firstOrNull { it.name.endsWith(".dmg") || it.name.endsWith(".zip") }?.browserDownloadUrl
}

@Serializable
data class ReleaseAsset(
    @SerialName("name")
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    @SerialName("size")
    val size: Long = 0,
)

/**
 * Result of checking for updates.
 */
sealed class UpdateCheckResult {
    data class UpdateAvailable(
        val updateInfo: UpdateInfo,
    ) : UpdateCheckResult()

    data object NoUpdateAvailable : UpdateCheckResult()

    data class Error(
        val message: String,
    ) : UpdateCheckResult()
}

/**
 * Service for checking for application updates via GitHub Releases API.
 */
object UpdateChecker {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    /**
     * Checks for the latest release on GitHub and compares with current version.
     */
    suspend fun checkForUpdates(): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/${AppVersion.GITHUB_OWNER}/${AppVersion.GITHUB_REPO}/releases/latest"
                logger.info { "Checking for updates at: $url" }

                val response =
                    httpClient.get(url) {
                        header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                        header(HttpHeaders.UserAgent, "Postest/${AppVersion.CURRENT}")
                    }

                when (response.status) {
                    HttpStatusCode.OK -> {
                        val updateInfo = json.decodeFromString<UpdateInfo>(response.bodyAsText())
                        logger.info { "Latest release: ${updateInfo.tagName}, current: ${AppVersion.CURRENT}" }

                        if (AppVersion.isNewerVersion(AppVersion.CURRENT, updateInfo.version)) {
                            logger.info { "Update available: ${updateInfo.version}" }
                            UpdateCheckResult.UpdateAvailable(updateInfo)
                        } else {
                            logger.info { "No update available" }
                            UpdateCheckResult.NoUpdateAvailable
                        }
                    }
                    HttpStatusCode.NotFound -> {
                        logger.info { "No releases found on GitHub" }
                        UpdateCheckResult.NoUpdateAvailable
                    }
                    else -> {
                        val errorBody = response.bodyAsText()
                        logger.warn { "GitHub API error: ${response.status} - $errorBody" }
                        UpdateCheckResult.Error("GitHub API returned ${response.status}")
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to check for updates: ${e.message}" }
                UpdateCheckResult.Error(e.message ?: "Unknown error occurred")
            }
        }
}
