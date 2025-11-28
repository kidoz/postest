package su.kidoz.postest.data.export

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.import.PostmanAuth
import su.kidoz.postest.data.import.PostmanAuthParam
import su.kidoz.postest.data.import.PostmanBody
import su.kidoz.postest.data.import.PostmanBodyOptions
import su.kidoz.postest.data.import.PostmanCollection
import su.kidoz.postest.data.import.PostmanFile
import su.kidoz.postest.data.import.PostmanFormDataParam
import su.kidoz.postest.data.import.PostmanGraphQL
import su.kidoz.postest.data.import.PostmanHeader
import su.kidoz.postest.data.import.PostmanInfo
import su.kidoz.postest.data.import.PostmanItem
import su.kidoz.postest.data.import.PostmanQueryParam
import su.kidoz.postest.data.import.PostmanRawOptions
import su.kidoz.postest.data.import.PostmanRequest
import su.kidoz.postest.data.import.PostmanUrl
import su.kidoz.postest.data.import.PostmanUrlEncodedParam
import su.kidoz.postest.data.import.PostmanVariable
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.Variable
import java.io.File
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Exporter for Postman Collection v2.1.0 format.
 * Converts Postest collections to Postman-compatible JSON.
 */
class PostmanExporter {
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
        }

    companion object {
        /** Postman Collection v2.1.0 schema URL */
        const val SCHEMA_V2_1 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    }

    /**
     * Export a collection to Postman Collection v2.1.0 JSON string.
     *
     * @param collection The collection to export
     * @return JSON string in Postman Collection v2.1.0 format
     */
    fun export(collection: RequestCollection): String {
        logger.info { "Exporting collection '${collection.name}' to Postman v2.1.0 format" }

        val postmanCollection = convertCollection(collection)
        val jsonString = json.encodeToString(postmanCollection)

        logger.info { "Successfully exported collection with ${countRequests(collection.items)} requests" }
        return jsonString
    }

    /**
     * Export a collection to a file.
     *
     * @param collection The collection to export
     * @param file The target file
     * @return Result indicating success or failure
     */
    fun exportToFile(
        collection: RequestCollection,
        file: File,
    ): Result<Unit> =
        try {
            val jsonString = export(collection)
            file.writeText(jsonString, Charsets.UTF_8)
            logger.info { "Exported collection to file: ${file.absolutePath}" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(e) { "Failed to export collection to file: ${e.message}" }
            Result.failure(ExportException("Failed to export collection: ${e.message}", e))
        }

    private fun convertCollection(collection: RequestCollection): PostmanCollection =
        PostmanCollection(
            info =
                PostmanInfo(
                    name = collection.name,
                    description = collection.description.ifBlank { null },
                    postmanId = UUID.randomUUID().toString(),
                    schema = SCHEMA_V2_1,
                ),
            item = collection.items.map { convertItem(it) },
            variable = collection.variables.map { convertVariable(it) }.ifEmpty { null },
            auth = collection.auth?.let { convertAuth(it) },
        )

    private fun convertItem(item: CollectionItem): PostmanItem =
        when (item) {
            is CollectionItem.Request ->
                PostmanItem(
                    name = item.name,
                    description = null,
                    request = convertRequest(item.request),
                    item = null,
                    auth = item.request.auth?.let { convertAuth(it) },
                )
            is CollectionItem.Folder ->
                PostmanItem(
                    name = item.name,
                    description = item.description.ifBlank { null },
                    request = null,
                    item = item.items.map { convertItem(it) },
                    auth = null,
                )
        }

    private fun convertRequest(request: HttpRequest): PostmanRequest =
        PostmanRequest(
            method = request.method.name,
            url = convertUrl(request),
            header = request.headers.map { convertHeader(it) }.ifEmpty { null },
            body = request.body?.let { convertBody(it) },
            auth = null, // Auth is set at item level
            description = null,
        )

    private fun convertUrl(request: HttpRequest): PostmanUrl {
        val urlString = request.url
        val parsed = parseUrl(urlString)

        return PostmanUrl(
            raw = buildRawUrl(request),
            protocol = parsed.protocol,
            host = parsed.host,
            path = parsed.path,
            query = request.queryParams.map { convertQueryParam(it) }.ifEmpty { null },
            variable = null,
        )
    }

    private fun buildRawUrl(request: HttpRequest): String {
        val base = request.url
        val enabledParams = request.queryParams.filter { it.enabled }

        return if (enabledParams.isEmpty()) {
            base
        } else {
            val queryString =
                enabledParams.joinToString("&") { param ->
                    "${param.key}=${param.value}"
                }
            if (base.contains("?")) {
                "$base&$queryString"
            } else {
                "$base?$queryString"
            }
        }
    }

    private data class ParsedUrl(
        val protocol: String?,
        val host: List<String>?,
        val path: List<String>?,
    )

    private fun parseUrl(url: String): ParsedUrl {
        val protocolMatch = Regex("^(https?)://").find(url)
        val protocol = protocolMatch?.groupValues?.get(1)

        val withoutProtocol =
            if (protocol != null) {
                url.removePrefix("$protocol://")
            } else {
                url
            }

        // Remove query string
        val withoutQuery = withoutProtocol.split("?").first()

        // Split host and path
        val parts = withoutQuery.split("/", limit = 2)
        val hostPart = parts.firstOrNull()?.takeIf { it.isNotBlank() }
        val pathPart = parts.getOrNull(1)

        val host = hostPart?.split(".")?.takeIf { it.isNotEmpty() }
        val path = pathPart?.split("/")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

        return ParsedUrl(protocol, host, path)
    }

    private fun convertQueryParam(param: KeyValue): PostmanQueryParam =
        PostmanQueryParam(
            key = param.key,
            value = param.value,
            description = param.description.ifBlank { null },
            disabled = if (param.enabled) null else true,
        )

    private fun convertHeader(header: KeyValue): PostmanHeader =
        PostmanHeader(
            key = header.key,
            value = header.value,
            description = header.description.ifBlank { null },
            disabled = if (header.enabled) null else true,
            type = "text",
        )

    private fun convertBody(body: RequestBody): PostmanBody =
        when (body) {
            is RequestBody.Json ->
                PostmanBody(
                    mode = "raw",
                    raw = body.content,
                    options =
                        PostmanBodyOptions(
                            raw = PostmanRawOptions(language = "json"),
                        ),
                )
            is RequestBody.Xml ->
                PostmanBody(
                    mode = "raw",
                    raw = body.content,
                    options =
                        PostmanBodyOptions(
                            raw = PostmanRawOptions(language = "xml"),
                        ),
                )
            is RequestBody.Raw ->
                PostmanBody(
                    mode = "raw",
                    raw = body.content,
                    options =
                        PostmanBodyOptions(
                            raw = PostmanRawOptions(language = guessLanguage(body.contentType)),
                        ),
                )
            is RequestBody.FormUrlEncoded ->
                PostmanBody(
                    mode = "urlencoded",
                    urlencoded =
                        body.fields.map { field ->
                            PostmanUrlEncodedParam(
                                key = field.key,
                                value = field.value,
                                description = field.description.ifBlank { null },
                                disabled = if (field.enabled) null else true,
                                type = "text",
                            )
                        },
                )
            is RequestBody.FormData ->
                PostmanBody(
                    mode = "formdata",
                    formdata =
                        body.fields.map { field ->
                            PostmanFormDataParam(
                                key = field.key,
                                value = if (field.type == FormFieldType.TEXT) field.value else null,
                                src = if (field.type == FormFieldType.FILE) field.value else null,
                                disabled = if (field.enabled) null else true,
                                type = if (field.type == FormFieldType.FILE) "file" else "text",
                            )
                        },
                )
            is RequestBody.Binary ->
                PostmanBody(
                    mode = "file",
                    file = PostmanFile(src = body.filePath),
                )
            is RequestBody.GraphQL ->
                PostmanBody(
                    mode = "graphql",
                    graphql =
                        PostmanGraphQL(
                            query = body.query,
                            variables = body.variables,
                        ),
                )
            is RequestBody.None -> PostmanBody(mode = null)
        }

    private fun guessLanguage(contentType: String?): String =
        when {
            contentType == null -> "text"
            contentType.contains("json") -> "json"
            contentType.contains("xml") -> "xml"
            contentType.contains("html") -> "html"
            contentType.contains("javascript") -> "javascript"
            else -> "text"
        }

    private fun convertAuth(auth: AuthConfig): PostmanAuth? =
        when (auth) {
            is AuthConfig.None -> PostmanAuth(type = "noauth")
            is AuthConfig.Basic ->
                PostmanAuth(
                    type = "basic",
                    basic =
                        listOf(
                            PostmanAuthParam(key = "username", value = auth.username, type = "string"),
                            PostmanAuthParam(key = "password", value = auth.password, type = "string"),
                        ),
                )
            is AuthConfig.Bearer ->
                PostmanAuth(
                    type = "bearer",
                    bearer =
                        listOf(
                            PostmanAuthParam(key = "token", value = auth.token, type = "string"),
                        ),
                )
            is AuthConfig.ApiKey ->
                PostmanAuth(
                    type = "apikey",
                    apikey =
                        listOf(
                            PostmanAuthParam(key = "key", value = auth.key, type = "string"),
                            PostmanAuthParam(key = "value", value = auth.value, type = "string"),
                            PostmanAuthParam(
                                key = "in",
                                value =
                                    when (auth.addTo) {
                                        AuthConfig.ApiKey.AddTo.HEADER -> "header"
                                        AuthConfig.ApiKey.AddTo.QUERY_PARAM -> "query"
                                    },
                                type = "string",
                            ),
                        ),
                )
        }

    private fun convertVariable(variable: Variable): PostmanVariable =
        PostmanVariable(
            key = variable.key,
            value = variable.value,
            description = null,
            disabled = if (variable.enabled) null else true,
            type = "string",
            id = variable.id,
        )

    private fun countRequests(items: List<CollectionItem>): Int =
        items.sumOf { item ->
            when (item) {
                is CollectionItem.Request -> 1
                is CollectionItem.Folder -> countRequests(item.items)
            }
        }
}

class ExportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
