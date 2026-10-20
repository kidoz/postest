package su.kidoz.postest.domain.usecase

import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.OAuth2Token
import su.kidoz.postest.domain.port.OAuth2TokenProvider
import su.kidoz.postest.util.VariableResolver

class AuthorizeOAuth2UseCase(
    private val tokenProvider: OAuth2TokenProvider,
    private val variableResolver: VariableResolver,
) {
    suspend fun authorize(
        config: AuthConfig.OAuth2,
        context: ExecutionContext,
    ): Result<OAuth2Token> = tokenProvider.authorize(resolve(config, context))

    private fun resolve(
        config: AuthConfig.OAuth2,
        context: ExecutionContext,
    ): AuthConfig.OAuth2 {
        fun resolve(value: String): String = variableResolver.resolve(value, context.environment, context.collectionVariables)
        return config.copy(
            authorizationUrl = resolve(config.authorizationUrl),
            tokenUrl = resolve(config.tokenUrl),
            clientId = resolve(config.clientId),
            clientSecret = resolve(config.clientSecret),
            scope = resolve(config.scope),
        )
    }
}
