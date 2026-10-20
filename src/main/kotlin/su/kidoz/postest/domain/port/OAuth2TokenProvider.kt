package su.kidoz.postest.domain.port

import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.OAuth2Token

/**
 * Obtains OAuth 2.0 tokens. [authorize] runs an interactive or explicit grant;
 * [token] resolves a usable token without prompting, refreshing it when possible.
 * Failures are [su.kidoz.postest.domain.model.OAuth2Exception] values.
 */
interface OAuth2TokenProvider {
    suspend fun authorize(config: AuthConfig.OAuth2): Result<OAuth2Token>

    suspend fun token(config: AuthConfig.OAuth2): Result<OAuth2Token>
}
