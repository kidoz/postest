package su.kidoz.postest.data.import

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import su.kidoz.postest.domain.model.*
import su.kidoz.postest.util.JsonFormatter
import su.kidoz.postest.util.XmlFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Unified importer for all Postman Collection formats.
 * Supports:
 * - v1.0.0 (uses 'requests' and 'folders' arrays)
 * - v2.0.0 (uses 'item' array)
 * - v2.1.0 (uses 'item' array, minor differences from v2.0)
 *
 * Automatically detects the version and delegates to the appropriate parser.
 */
class PostmanImporter {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    private val v1Importer = PostmanV1Importer()

    /**
     * Import a Postman collection from JSON string.
     * Automatically detects the version and parses accordingly.
     *
     * @param jsonString The JSON content of a Postman collection file
     * @return Result containing the imported RequestCollection or an error
     */
    fun import(jsonString: String): Result<RequestCollection> {
        val version = PostmanVersionDetector.detectVersion(jsonString)
        logger.info { "Detected Postman collection version: $version" }

        return when (version) {
            PostmanVersionDetector.PostmanVersion.V1 -> {
                v1Importer.import(jsonString)
            }
            PostmanVersionDetector.PostmanVersion.V2_0,
            PostmanVersionDetector.PostmanVersion.V2_1,
            -> {
                importV2(jsonString)
            }
            PostmanVersionDetector.PostmanVersion.UNKNOWN -> {
                // Try v2.x first as it's more common
                logger.warn { "Unknown Postman version, attempting v2.x import" }
                val v2Result = importV2(jsonString)
                if (v2Result.isSuccess) {
                    v2Result
                } else {
                    // Fall back to v1
                    logger.warn { "v2.x import failed, attempting v1.0 import" }
                    v1Importer.import(jsonString)
                }
            }
        }
    }

    /**
     * Detect if the given JSON string is a valid Postman collection.
     */
    fun isPostmanCollection(jsonString: String): Boolean = PostmanVersionDetector.isPostmanCollection(jsonString)

    /**
     * Get the detected version of a Postman collection.
     */
    fun detectVersion(jsonString: String): PostmanVersionDetector.PostmanVersion = PostmanVersionDetector.detectVersion(jsonString)

    // ========== V2.x Import Logic ==========

    private fun importV2(jsonString: String): Result<RequestCollection> =
        try {
            logger.info { "Starting Postman v2.x collection import" }
            val postmanCollection = json.decodeFromString<PostmanCollection>(jsonString)
            val collection = convertV2Collection(postmanCollection)
            logger.info { "Successfully imported v2.x collection '${collection.name}' with ${countRequests(collection.items)} requests" }
            Result.success(collection)
        } catch (e: Exception) {
            logger.error(e) { "Failed to import Postman v2.x collection: ${e.message}" }
            Result.failure(ImportException("Failed to parse Postman v2.x collection: ${e.message}", e))
        }

    private fun convertV2Collection(postman: PostmanCollection): RequestCollection =
        RequestCollection(
            id = UUID.randomUUID().toString(),
            name = postman.info.name,
            description = postman.info.description ?: "",
            items = postman.item.map { convertV2Item(it, postman.auth) },
            variables = postman.variable?.mapNotNull { convertVariable(it) } ?: emptyList(),
            auth = postman.auth?.let { convertAuth(it) },
        )

    private fun convertV2Item(
        postmanItem: PostmanItem,
        parentAuth: PostmanAuth?,
    ): CollectionItem =
        if (postmanItem.isFolder) {
            CollectionItem.Folder(
                id = UUID.randomUUID().toString(),
                name = postmanItem.name,
                description = postmanItem.description ?: "",
                items = postmanItem.item?.map { convertV2Item(it, postmanItem.auth ?: parentAuth) } ?: emptyList(),
            )
        } else {
            val request = postmanItem.request
            CollectionItem.Request(
                id = UUID.randomUUID().toString(),
                name = postmanItem.name,
                request = convertV2Request(request, postmanItem.auth ?: parentAuth),
            )
        }

    private fun convertV2Request(
        postmanRequest: PostmanRequest?,
        parentAuth: PostmanAuth?,
    ): HttpRequest {
        if (postmanRequest == null) {
            return HttpRequest()
        }

        val url = postmanRequest.url?.toUrlString() ?: ""
        val queryParams = extractQueryParams(postmanRequest.url)

        return HttpRequest(
            id = UUID.randomUUID().toString(),
            name = "",
            method = convertMethod(postmanRequest.method),
            url = cleanUrl(url),
            headers = postmanRequest.header?.mapNotNull { convertHeader(it) } ?: emptyList(),
            queryParams = queryParams,
            body = convertBody(postmanRequest.body),
            auth =
                postmanRequest.auth?.let { convertAuth(it) }
                    ?: parentAuth?.let { convertAuth(it) },
        )
    }

    private fun convertMethod(method: String): HttpMethod =
        try {
            HttpMethod.valueOf(method.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Unknown HTTP method '$method', defaulting to GET" }
            HttpMethod.GET
        }

    private fun convertHeader(header: PostmanHeader): KeyValue? {
        if (header.disabled == true) {
            return KeyValue(
                key = header.key,
                value = header.value,
                enabled = false,
                description = header.description ?: "",
            )
        }
        return KeyValue(
            key = header.key,
            value = header.value,
            enabled = true,
            description = header.description ?: "",
        )
    }

    private fun extractQueryParams(url: PostmanUrl?): List<KeyValue> =
        url?.query?.map { param ->
            KeyValue(
                key = param.key ?: "",
                value = param.value ?: "",
                enabled = param.disabled != true,
                description = param.description ?: "",
            )
        } ?: emptyList()

    private fun cleanUrl(url: String): String {
        // Remove query string from URL (we handle params separately)
        return url.split("?").firstOrNull() ?: url
    }

    private fun convertBody(body: PostmanBody?): RequestBody? {
        if (body == null) return null

        return when (body.mode) {
            "raw" -> {
                val language = body.options?.raw?.language
                val content = body.raw ?: ""
                when (language) {
                    "json" -> RequestBody.Json(formatJsonSafely(content))
                    "xml" -> RequestBody.Xml(formatXmlSafely(content))
                    else -> {
                        // Check if content looks like JSON
                        val trimmed = content.trim()
                        when {
                            trimmed.startsWith("{") || trimmed.startsWith("[") ->
                                RequestBody.Json(formatJsonSafely(content))
                            trimmed.startsWith("<?xml") || trimmed.startsWith("<") ->
                                RequestBody.Xml(formatXmlSafely(content))
                            else ->
                                RequestBody.Raw(
                                    content = content,
                                    contentType = guessContentType(language),
                                )
                        }
                    }
                }
            }
            "urlencoded" -> {
                RequestBody.FormUrlEncoded(
                    fields =
                        body.urlencoded?.map { param ->
                            KeyValue(
                                key = param.key,
                                value = param.value ?: "",
                                enabled = param.disabled != true,
                                description = param.description ?: "",
                            )
                        } ?: emptyList(),
                )
            }
            "formdata" -> {
                RequestBody.FormData(
                    fields =
                        body.formdata?.map { param ->
                            FormField(
                                key = param.key,
                                value = param.value ?: param.src ?: "",
                                type = if (param.type == "file") FormFieldType.FILE else FormFieldType.TEXT,
                                enabled = param.disabled != true,
                            )
                        } ?: emptyList(),
                )
            }
            "graphql" -> {
                RequestBody.GraphQL(
                    query = body.graphql?.query ?: "",
                    variables = body.graphql?.variables,
                )
            }
            "file" -> {
                RequestBody.Binary(filePath = body.file?.src ?: "")
            }
            else -> null
        }
    }

    private fun guessContentType(language: String?): String =
        when (language?.lowercase()) {
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html" -> "text/html"
            "javascript", "js" -> "application/javascript"
            "text" -> "text/plain"
            else -> "text/plain"
        }

    private fun convertAuth(auth: PostmanAuth): AuthConfig? =
        when (auth.type.lowercase()) {
            "noauth", "none" -> AuthConfig.None
            "basic" -> {
                val username = auth.basic?.find { it.key == "username" }?.value ?: ""
                val password = auth.basic?.find { it.key == "password" }?.value ?: ""
                AuthConfig.Basic(username = username, password = password)
            }
            "bearer" -> {
                val token = auth.bearer?.find { it.key == "token" }?.value ?: ""
                AuthConfig.Bearer(token = token)
            }
            "apikey" -> {
                val key = auth.apikey?.find { it.key == "key" }?.value ?: ""
                val value = auth.apikey?.find { it.key == "value" }?.value ?: ""
                val addTo = auth.apikey?.find { it.key == "in" }?.value
                AuthConfig.ApiKey(
                    key = key,
                    value = value,
                    addTo = if (addTo == "query") AuthConfig.ApiKey.AddTo.QUERY_PARAM else AuthConfig.ApiKey.AddTo.HEADER,
                )
            }
            else -> {
                logger.warn { "Unsupported auth type '${auth.type}', skipping" }
                null
            }
        }

    private fun convertVariable(variable: PostmanVariable): Variable? {
        val key = variable.key ?: return null
        return Variable(
            id = variable.id ?: UUID.randomUUID().toString(),
            key = key,
            value = variable.value ?: "",
            type = VariableType.DEFAULT,
            enabled = variable.disabled != true,
        )
    }

    private fun countRequests(items: List<CollectionItem>): Int =
        items.sumOf { item ->
            when (item) {
                is CollectionItem.Request -> 1
                is CollectionItem.Folder -> countRequests(item.items)
            }
        }

    /**
     * Safely format JSON content, returning original if formatting fails.
     */
    private fun formatJsonSafely(content: String): String =
        if (content.isBlank()) {
            content
        } else {
            try {
                JsonFormatter.format(content)
            } catch (e: Exception) {
                logger.debug(e) { "Failed to format JSON on import, keeping original" }
                content
            }
        }

    /**
     * Safely format XML content, returning original if formatting fails.
     */
    private fun formatXmlSafely(content: String): String =
        if (content.isBlank()) {
            content
        } else {
            try {
                XmlFormatter.format(content)
            } catch (e: Exception) {
                logger.debug(e) { "Failed to format XML on import, keeping original" }
                content
            }
        }
}

class ImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
