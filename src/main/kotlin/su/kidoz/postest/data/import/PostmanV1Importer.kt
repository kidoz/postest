package su.kidoz.postest.data.import

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import su.kidoz.postest.domain.model.*
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Importer for Postman Collection v1.0.0 format.
 *
 * Key differences from v2.x:
 * - Uses separate 'requests' and 'folders' arrays
 * - Uses 'order' array with UUIDs to maintain sequence
 * - Headers are stored as newline-separated string or structured array
 * - Auth uses 'currentHelper' and 'helperAttributes' instead of 'auth' object
 */
class PostmanV1Importer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    /**
     * Import a Postman v1.0.0 collection from JSON string.
     */
    fun import(jsonString: String): Result<RequestCollection> =
        try {
            logger.info { "Starting Postman v1.0.0 collection import" }
            val postmanCollection = json.decodeFromString<PostmanV1Collection>(jsonString)
            val collection = convertCollection(postmanCollection)
            logger.info { "Successfully imported v1.0.0 collection '${collection.name}' with ${countRequests(collection.items)} requests" }
            Result.success(collection)
        } catch (e: Exception) {
            logger.error(e) { "Failed to import Postman v1.0.0 collection: ${e.message}" }
            Result.failure(ImportException("Failed to parse Postman v1.0.0 collection: ${e.message}", e))
        }

    private fun convertCollection(postman: PostmanV1Collection): RequestCollection {
        // Build folder map for quick lookup
        val folderMap = postman.folders.associateBy { it.id }

        // Build request map for quick lookup
        val requestMap = postman.requests.associateBy { it.id }

        // Build folder hierarchy
        val items = buildItems(postman, folderMap, requestMap)

        return RequestCollection(
            id = UUID.randomUUID().toString(),
            name = postman.name,
            description = postman.description ?: "",
            items = items,
            variables = postman.variables?.mapNotNull { convertVariable(it) } ?: emptyList(),
            auth = postman.auth?.let { convertAuth(it) },
        )
    }

    private fun buildItems(
        collection: PostmanV1Collection,
        folderMap: Map<String, PostmanV1Folder>,
        requestMap: Map<String, PostmanV1Request>,
    ): List<CollectionItem> {
        val items = mutableListOf<CollectionItem>()
        val processedFolders = mutableSetOf<String>()
        val processedRequests = mutableSetOf<String>()

        // Process folders in order (if folders_order exists)
        val folderOrder = collection.foldersOrder ?: collection.folders.map { it.id }
        for (folderId in folderOrder) {
            val folder = folderMap[folderId] ?: continue
            if (folderId in processedFolders) continue
            processedFolders.add(folderId)

            val folderItems = buildFolderItems(folder, requestMap, processedRequests)
            items.add(
                CollectionItem.Folder(
                    id = UUID.randomUUID().toString(),
                    name = folder.name,
                    description = folder.description ?: "",
                    items = folderItems,
                ),
            )
        }

        // Process root-level requests (those in collection.order but not in any folder)
        for (requestId in collection.order) {
            if (requestId in processedRequests) continue
            val request = requestMap[requestId] ?: continue

            // Skip if request belongs to a folder
            if (request.folder != null || request.folderId != null) continue

            processedRequests.add(requestId)
            items.add(convertRequest(request))
        }

        // Add any remaining requests not in order array
        for (request in collection.requests) {
            if (request.id in processedRequests) continue
            if (request.folder != null || request.folderId != null) continue
            processedRequests.add(request.id)
            items.add(convertRequest(request))
        }

        return items
    }

    private fun buildFolderItems(
        folder: PostmanV1Folder,
        requestMap: Map<String, PostmanV1Request>,
        processedRequests: MutableSet<String>,
    ): List<CollectionItem> {
        val items = mutableListOf<CollectionItem>()

        // Process requests in folder's order
        for (requestId in folder.order) {
            if (requestId in processedRequests) continue
            val request = requestMap[requestId] ?: continue
            processedRequests.add(requestId)
            items.add(convertRequest(request))
        }

        // Add any requests that reference this folder but aren't in order
        for ((id, request) in requestMap) {
            if (id in processedRequests) continue
            if (request.folder == folder.id || request.folderId == folder.id) {
                processedRequests.add(id)
                items.add(convertRequest(request))
            }
        }

        return items
    }

    private fun convertRequest(postmanRequest: PostmanV1Request): CollectionItem.Request {
        val headers = parseHeaders(postmanRequest)
        val queryParams = parseQueryParams(postmanRequest)
        val body = parseBody(postmanRequest)
        val auth = parseAuth(postmanRequest)

        // Clean URL (remove query string if we parsed params separately)
        val cleanUrl = postmanRequest.url.split("?").firstOrNull() ?: postmanRequest.url

        return CollectionItem.Request(
            id = UUID.randomUUID().toString(),
            name = postmanRequest.name,
            request =
                HttpRequest(
                    id = UUID.randomUUID().toString(),
                    name = postmanRequest.name,
                    method = convertMethod(postmanRequest.method),
                    url = cleanUrl,
                    headers = headers,
                    queryParams = queryParams,
                    body = body,
                    auth = auth,
                ),
        )
    }

    private fun parseHeaders(request: PostmanV1Request): List<KeyValue> {
        // Try structured headerData first
        if (!request.headerData.isNullOrEmpty()) {
            return request.headerData.map { header ->
                KeyValue(
                    key = header.key,
                    value = header.value,
                    enabled = header.enabled != false,
                    description = header.description ?: "",
                )
            }
        }

        // Fall back to parsing string headers
        val headersString = request.headers ?: return emptyList()
        return headersString
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    KeyValue(
                        key = parts[0].trim(),
                        value = parts[1].trim(),
                        enabled = true,
                    )
                } else {
                    null
                }
            }
    }

    private fun parseQueryParams(request: PostmanV1Request): List<KeyValue> {
        // Try structured queryParams first
        if (!request.queryParams.isNullOrEmpty()) {
            return request.queryParams.map { param ->
                KeyValue(
                    key = param.key,
                    value = param.value ?: "",
                    enabled = param.enabled != false,
                    description = param.description ?: "",
                )
            }
        }

        // Fall back to parsing from URL
        val url = request.url
        val queryStart = url.indexOf("?")
        if (queryStart == -1) return emptyList()

        val queryString = url.substring(queryStart + 1)
        return queryString
            .split("&")
            .filter { it.isNotBlank() }
            .map { param ->
                val parts = param.split("=", limit = 2)
                KeyValue(
                    key = parts[0],
                    value = parts.getOrElse(1) { "" },
                    enabled = true,
                )
            }
    }

    private fun parseBody(request: PostmanV1Request): RequestBody? {
        val dataMode = request.dataMode ?: return null

        return when (dataMode.lowercase()) {
            "raw" -> {
                val content = request.rawModeData ?: request.data ?: ""
                // Try to detect if it's JSON
                if (content.trim().let { it.startsWith("{") || it.startsWith("[") }) {
                    RequestBody.Json(content)
                } else {
                    RequestBody.Raw(content = content, contentType = "text/plain")
                }
            }
            "urlencoded" -> {
                // In v1, urlencoded data might be in 'data' as a string or parsed
                val dataString = request.data ?: ""
                val fields =
                    dataString
                        .split("&")
                        .filter { it.isNotBlank() }
                        .map { param ->
                            val parts = param.split("=", limit = 2)
                            KeyValue(
                                key = parts[0],
                                value = parts.getOrElse(1) { "" },
                                enabled = true,
                            )
                        }
                RequestBody.FormUrlEncoded(fields = fields)
            }
            "params", "formdata" -> {
                val dataString = request.data ?: ""
                val fields =
                    dataString
                        .split("&")
                        .filter { it.isNotBlank() }
                        .map { param ->
                            val parts = param.split("=", limit = 2)
                            FormField(
                                key = parts[0],
                                value = parts.getOrElse(1) { "" },
                                type = FormFieldType.TEXT,
                                enabled = true,
                            )
                        }
                RequestBody.FormData(fields = fields)
            }
            "binary" -> {
                RequestBody.Binary(filePath = request.data ?: "")
            }
            "graphql" -> {
                request.graphqlModeData?.let { graphql ->
                    RequestBody.GraphQL(
                        query = graphql.query ?: "",
                        variables = graphql.variables,
                    )
                }
            }
            else -> null
        }
    }

    private fun parseAuth(request: PostmanV1Request): AuthConfig? {
        // Try new-style auth first
        request.auth?.let { auth ->
            return convertAuth(auth)
        }

        // Fall back to v1-style currentHelper/helperAttributes
        val helper = request.currentHelper ?: return null
        val attrs = request.helperAttributes ?: return null

        return when (helper.lowercase()) {
            "basicauth", "basic" -> {
                AuthConfig.Basic(
                    username = attrs.username ?: "",
                    password = attrs.password ?: "",
                )
            }
            "bearerauth", "bearer" -> {
                AuthConfig.Bearer(token = attrs.accessToken ?: attrs.token ?: "")
            }
            "oauth2" -> {
                AuthConfig.Bearer(token = attrs.accessToken ?: "")
            }
            else -> null
        }
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
            else -> null
        }

    private fun convertMethod(method: String): HttpMethod =
        try {
            HttpMethod.valueOf(method.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn { "Unknown HTTP method '$method', defaulting to GET" }
            HttpMethod.GET
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
}
