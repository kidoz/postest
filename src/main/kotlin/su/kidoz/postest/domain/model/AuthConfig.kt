package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed class AuthConfig {
    @Serializable
    data object None : AuthConfig()

    @Serializable
    data class Basic(
        val username: String,
        val password: String,
    ) : AuthConfig()

    @Serializable
    data class Bearer(
        val token: String,
    ) : AuthConfig()

    @Serializable
    data class ApiKey(
        val key: String,
        val value: String,
        val addTo: AddTo = AddTo.HEADER,
    ) : AuthConfig() {
        @Serializable
        enum class AddTo { HEADER, QUERY_PARAM }
    }

    @Serializable
    data class OAuth2(
        val grantType: OAuth2GrantType = OAuth2GrantType.AUTHORIZATION_CODE,
        val authorizationUrl: String = "",
        val tokenUrl: String = "",
        val clientId: String = "",
        val clientSecret: String = "",
        val scope: String = "",
        val clientSecretInBody: Boolean = false,
        val accessToken: String = "",
        val refreshToken: String = "",
        val tokenType: String = "Bearer",
        val expiresAt: Long = 0L,
    ) : AuthConfig()
}

@Serializable
enum class OAuth2GrantType {
    AUTHORIZATION_CODE,
    CLIENT_CREDENTIALS,
}

/** A token obtained from an OAuth 2.0 token endpoint. */
data class OAuth2Token(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val scope: String = "",
)

enum class OAuth2Failure(
    val description: String,
) {
    INVALID_ENDPOINT("OAuth 2.0 endpoints must use HTTPS"),
    BROWSER_UNAVAILABLE("Could not open the system browser"),
    TIMED_OUT("Authorization timed out"),
    REJECTED("Authorization was rejected"),
    TOKEN_REQUEST_FAILED("OAuth 2.0 token request failed"),
    AUTHORIZATION_REQUIRED("OAuth 2.0 authorization is required"),
    TOKEN_EXPIRED("OAuth 2.0 token has expired. Authorize again"),
}

/** Fixed-message OAuth failure; never carries endpoint URLs, codes, or token values. */
class OAuth2Exception(
    val failure: OAuth2Failure,
) : Exception(failure.description)
