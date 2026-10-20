package su.kidoz.postest.data.http

import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.OAuth2Failure
import su.kidoz.postest.domain.model.OAuth2Token
import su.kidoz.postest.domain.port.OAuth2TokenProvider

/**
 * Returns a usable token for the request. With a provider, refreshes silently when possible;
 * otherwise only a stored, unexpired token is accepted.
 */
internal suspend fun resolveOAuth2Token(
    config: AuthConfig.OAuth2,
    provider: OAuth2TokenProvider?,
    resolve: (String) -> String,
    now: () -> Long = System::currentTimeMillis,
): OAuth2Token {
    val resolved =
        config.copy(
            authorizationUrl = resolve(config.authorizationUrl),
            tokenUrl = resolve(config.tokenUrl),
            clientId = resolve(config.clientId),
            clientSecret = resolve(config.clientSecret),
            scope = resolve(config.scope),
            accessToken = resolve(config.accessToken),
            refreshToken = resolve(config.refreshToken),
        )
    if (provider != null) return provider.token(resolved).getOrElse { throw it }.validated()
    if (resolved.accessToken.isBlank()) throw OAuth2Exception(OAuth2Failure.AUTHORIZATION_REQUIRED)
    if (resolved.expiresAt > 0 && now() >= resolved.expiresAt) throw OAuth2Exception(OAuth2Failure.TOKEN_EXPIRED)
    return OAuth2Token(
        accessToken = resolved.accessToken,
        tokenType = resolved.tokenType,
        refreshToken = resolved.refreshToken,
        expiresAt = resolved.expiresAt,
        scope = resolved.scope,
    ).validated()
}

/** Token endpoint responses are untrusted; reject values that could inject headers. */
private fun OAuth2Token.validated(): OAuth2Token {
    fun unsafe(value: String): Boolean = value.any { it == '\r' || it == '\n' || it == '\u0000' }
    if (unsafe(accessToken) || tokenType.any { it.isISOControl() }) {
        throw OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED)
    }
    return this
}

internal fun oauth2Authorization(token: OAuth2Token): String {
    val scheme = token.tokenType.takeIf { it.isNotBlank() } ?: "Bearer"
    return "$scheme ${token.accessToken}"
}
