package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RequestCollection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val items: List<CollectionItem> = emptyList(),
    val variables: List<Variable> = emptyList(),
    val auth: AuthConfig? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
sealed class CollectionItem {
    abstract val id: String
    abstract val name: String

    @Serializable
    data class Request(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        val request: HttpRequest,
    ) : CollectionItem()

    @Serializable
    data class Folder(
        override val id: String = UUID.randomUUID().toString(),
        override val name: String,
        val items: List<CollectionItem> = emptyList(),
        val description: String = "",
    ) : CollectionItem()
}
