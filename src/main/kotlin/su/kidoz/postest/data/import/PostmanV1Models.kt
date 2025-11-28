package su.kidoz.postest.data.import

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Postman Collection Format v1.0.0 data models
 * Based on: https://schema.postman.com/collection/json/v1.0.0/draft-07/collection.json
 *
 * Key differences from v2.x:
 * - Uses separate 'requests' and 'folders' arrays instead of unified 'item' array
 * - Uses 'order' array with UUIDs to maintain sequence
 * - Flat structure with references instead of nested hierarchy
 */

@Serializable
data class PostmanV1Collection(
    val id: String,
    val name: String,
    val description: String? = null,
    val order: List<String> = emptyList(),
    val folders: List<PostmanV1Folder> = emptyList(),
    val requests: List<PostmanV1Request> = emptyList(),
    val variables: List<PostmanVariable>? = null,
    val auth: PostmanAuth? = null,
    @SerialName("folders_order")
    val foldersOrder: List<String>? = null,
    val timestamp: Long? = null,
    val owner: String? = null,
    val remoteLink: String? = null,
    @SerialName("public")
    val isPublic: Boolean? = null,
)

@Serializable
data class PostmanV1Folder(
    val id: String,
    val name: String,
    val description: String? = null,
    val order: List<String> = emptyList(),
    @SerialName("folders_order")
    val foldersOrder: List<String>? = null,
    val collection: String? = null,
    @SerialName("collection_id")
    val collectionId: String? = null,
    val owner: String? = null,
)

@Serializable
data class PostmanV1Request(
    val id: String,
    val name: String,
    val description: String? = null,
    val method: String = "GET",
    val url: String = "",
    val headers: String? = null, // In v1, headers are a string with newline-separated key:value pairs
    val headerData: List<PostmanV1Header>? = null, // Alternative structured format
    val data: String? = null, // Raw body data
    val dataMode: String? = null, // "raw", "urlencoded", "params", "binary"
    val rawModeData: String? = null,
    @SerialName("graphqlModeData")
    val graphqlModeData: PostmanGraphQL? = null,
    val collectionId: String? = null,
    val collection: String? = null,
    val folder: String? = null, // Folder ID this request belongs to
    val folderId: String? = null,
    val responses: List<PostmanV1Response>? = null,
    val currentHelper: String? = null, // Auth type in v1
    val helperAttributes: PostmanV1HelperAttributes? = null,
    val auth: PostmanAuth? = null,
    val events: List<PostmanV1Event>? = null,
    val pathVariables: Map<String, String>? = null,
    val pathVariableData: List<PostmanVariable>? = null,
    val queryParams: List<PostmanV1QueryParam>? = null,
    val preRequestScript: String? = null,
    val tests: String? = null,
    val time: Long? = null,
    val dataDisabled: Boolean? = null,
)

@Serializable
data class PostmanV1Header(
    val key: String,
    val value: String,
    val description: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class PostmanV1QueryParam(
    val key: String,
    val value: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class PostmanV1Response(
    val id: String? = null,
    val name: String? = null,
    val status: String? = null,
    val responseCode: PostmanV1ResponseCode? = null,
    val time: Long? = null,
    val headers: List<PostmanV1Header>? = null,
    val cookies: List<PostmanV1Cookie>? = null,
    val mime: String? = null,
    val text: String? = null,
    val language: String? = null,
    val rawDataType: String? = null,
    val requestObject: String? = null,
)

@Serializable
data class PostmanV1ResponseCode(
    val code: Int,
    val name: String? = null,
)

@Serializable
data class PostmanV1Cookie(
    val domain: String? = null,
    val path: String? = null,
    val expires: String? = null,
    val httpOnly: Boolean? = null,
    val secure: Boolean? = null,
    val value: String? = null,
    val key: String? = null,
    val name: String? = null,
)

@Serializable
data class PostmanV1HelperAttributes(
    val id: String? = null,
    // Basic auth
    val username: String? = null,
    val password: String? = null,
    val saveHelperData: Boolean? = null,
    // OAuth 1
    val consumerKey: String? = null,
    val consumerSecret: String? = null,
    val token: String? = null,
    val tokenSecret: String? = null,
    val signatureMethod: String? = null,
    val timestamp: String? = null,
    val nonce: String? = null,
    val version: String? = null,
    val realm: String? = null,
    val encodeOAuthSign: Boolean? = null,
    // OAuth 2
    val accessToken: String? = null,
    val tokenType: String? = null,
    val addTokenTo: String? = null,
    // Digest auth
    val algorithm: String? = null,
    val qop: String? = null,
    val nonceCount: String? = null,
    val clientNonce: String? = null,
    val opaque: String? = null,
    // AWS
    val accessKey: String? = null,
    val secretKey: String? = null,
    val region: String? = null,
    val service: String? = null,
    // Hawk
    val authId: String? = null,
    val authKey: String? = null,
    val ext: String? = null,
    val app: String? = null,
    val dlg: String? = null,
)

@Serializable
data class PostmanV1Event(
    val listen: String, // "prerequest" or "test"
    val script: PostmanV1Script? = null,
)

@Serializable
data class PostmanV1Script(
    val id: String? = null,
    val type: String? = null,
    val exec: List<String>? = null,
)
