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
}
