package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ClientCertConfig(
    val certPath: String,
    val keyPath: String = "",
    val passphrase: String = "",
)
