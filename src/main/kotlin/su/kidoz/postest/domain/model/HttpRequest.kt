package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HttpRequest(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Request",
    val method: HttpMethod = HttpMethod.GET,
    val url: String = "",
    val headers: List<KeyValue> = emptyList(),
    val queryParams: List<KeyValue> = emptyList(),
    val body: RequestBody? = null,
    val auth: AuthConfig? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS,
}

@Serializable
data class KeyValue(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    val description: String = "",
)

@Serializable
sealed class RequestBody {
    @Serializable
    data class Json(
        val content: String,
    ) : RequestBody()

    @Serializable
    data class Xml(
        val content: String,
    ) : RequestBody()

    @Serializable
    data class FormData(
        val fields: List<FormField>,
    ) : RequestBody()

    @Serializable
    data class FormUrlEncoded(
        val fields: List<KeyValue>,
    ) : RequestBody()

    @Serializable
    data class Raw(
        val content: String,
        val contentType: String,
    ) : RequestBody()

    @Serializable
    data class Binary(
        val filePath: String,
    ) : RequestBody()

    @Serializable
    data class GraphQL(
        val query: String,
        val variables: String? = null,
    ) : RequestBody()

    @Serializable
    data object None : RequestBody()
}

@Serializable
data class FormField(
    val key: String,
    val value: String,
    val type: FormFieldType = FormFieldType.TEXT,
    val enabled: Boolean = true,
)

@Serializable
enum class FormFieldType {
    TEXT,
    FILE,
}
