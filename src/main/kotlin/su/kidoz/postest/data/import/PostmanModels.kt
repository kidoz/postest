package su.kidoz.postest.data.import

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Postman Collection Format v2.1.0 data models
 * Based on: https://schema.postman.com/collection/json/v2.1.0/draft-07/docs/index.html
 */

@Serializable
data class PostmanCollection(
    val info: PostmanInfo,
    val item: List<PostmanItem> = emptyList(),
    val variable: List<PostmanVariable>? = null,
    val auth: PostmanAuth? = null,
)

@Serializable
data class PostmanInfo(
    val name: String,
    val description: String? = null,
    @SerialName("_postman_id")
    val postmanId: String? = null,
    val schema: String? = null,
)

@Serializable
data class PostmanItem(
    val name: String,
    val description: String? = null,
    // Request item
    val request: PostmanRequest? = null,
    val response: List<PostmanResponse>? = null,
    // Folder item (has nested items)
    val item: List<PostmanItem>? = null,
    val auth: PostmanAuth? = null,
) {
    val isFolder: Boolean get() = item != null && request == null
}

@Serializable
data class PostmanRequest(
    val method: String = "GET",
    val url: PostmanUrl? = null,
    val header: List<PostmanHeader>? = null,
    val body: PostmanBody? = null,
    val auth: PostmanAuth? = null,
    val description: String? = null,
)

@Serializable(with = PostmanUrlSerializer::class)
data class PostmanUrl(
    val raw: String? = null,
    val protocol: String? = null,
    val host: List<String>? = null,
    val path: List<String>? = null,
    val query: List<PostmanQueryParam>? = null,
    val variable: List<PostmanVariable>? = null,
) {
    fun toUrlString(): String =
        raw ?: buildString {
            if (protocol != null) append("$protocol://")
            if (host != null) append(host.joinToString("."))
            if (path != null) append("/" + path.joinToString("/"))
            if (!query.isNullOrEmpty()) {
                append("?")
                append(
                    query
                        .filter { it.disabled != true }
                        .joinToString("&") { "${it.key}=${it.value ?: ""}" },
                )
            }
        }
}

@Serializable
data class PostmanQueryParam(
    val key: String? = null,
    val value: String? = null,
    val description: String? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class PostmanHeader(
    val key: String,
    val value: String,
    val description: String? = null,
    val disabled: Boolean? = null,
    val type: String? = null,
)

@Serializable
data class PostmanBody(
    val mode: String? = null,
    val raw: String? = null,
    val urlencoded: List<PostmanUrlEncodedParam>? = null,
    val formdata: List<PostmanFormDataParam>? = null,
    val graphql: PostmanGraphQL? = null,
    val file: PostmanFile? = null,
    val options: PostmanBodyOptions? = null,
)

@Serializable
data class PostmanBodyOptions(
    val raw: PostmanRawOptions? = null,
)

@Serializable
data class PostmanRawOptions(
    val language: String? = null,
)

@Serializable
data class PostmanUrlEncodedParam(
    val key: String,
    val value: String? = null,
    val description: String? = null,
    val disabled: Boolean? = null,
    val type: String? = null,
)

@Serializable
data class PostmanFormDataParam(
    val key: String,
    val value: String? = null,
    val src: String? = null,
    val description: String? = null,
    val disabled: Boolean? = null,
    val type: String? = null,
    val contentType: String? = null,
)

@Serializable
data class PostmanGraphQL(
    val query: String? = null,
    val variables: String? = null,
)

@Serializable
data class PostmanFile(
    val src: String? = null,
    val content: String? = null,
)

@Serializable
data class PostmanAuth(
    val type: String,
    val basic: List<PostmanAuthParam>? = null,
    val bearer: List<PostmanAuthParam>? = null,
    val apikey: List<PostmanAuthParam>? = null,
    val oauth2: List<PostmanAuthParam>? = null,
    val noauth: JsonElement? = null,
)

@Serializable
data class PostmanAuthParam(
    val key: String,
    val value: String? = null,
    val type: String? = null,
)

@Serializable
data class PostmanVariable(
    val key: String? = null,
    val value: String? = null,
    val description: String? = null,
    val disabled: Boolean? = null,
    val type: String? = null,
    val id: String? = null,
)

@Serializable
data class PostmanResponse(
    val name: String? = null,
    val originalRequest: PostmanRequest? = null,
    val status: String? = null,
    val code: Int? = null,
    val header: List<PostmanHeader>? = null,
    val body: String? = null,
)
