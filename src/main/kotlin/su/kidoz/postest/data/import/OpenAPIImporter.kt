package su.kidoz.postest.data.import

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import su.kidoz.postest.util.JsonFormatter
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Importer for OpenAPI 3.x specifications.
 * Converts OpenAPI specs to Postest request collections.
 *
 * Supports:
 * - OpenAPI 3.0.x
 * - OpenAPI 3.1.x
 * - JSON and YAML formats (YAML requires pre-conversion)
 *
 * Features:
 * - Generates requests for each path/operation
 * - Groups requests by tags into folders
 * - Extracts query, header, and path parameters
 * - Generates example request bodies from schemas
 * - Maps security schemes to auth configurations
 */
class OpenAPIImporter {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    /**
     * Check if the JSON string appears to be an OpenAPI specification.
     */
    fun isOpenAPISpec(content: String): Boolean =
        runCatching {
            val trimmed = content.trimStart()
            val asJson =
                when {
                    trimmed.startsWith("{") -> trimmed
                    trimmed.startsWith("---") || trimmed.startsWith("openapi:") -> {
                        yamlToJson(trimmed) ?: return false
                    }
                    else -> return false
                }
            val jsonElement = json.parseToJsonElement(asJson)
            if (jsonElement !is JsonObject) return false
            val openapi = jsonElement["openapi"]?.jsonPrimitive?.contentOrNull
            openapi?.startsWith("3.") == true
        }.getOrElse { false }

    /**
     * Import an OpenAPI specification from JSON string.
     */
    fun import(content: String): Result<RequestCollection> {
        return try {
            logger.info { "Starting OpenAPI specification import" }
            val normalizedJson =
                when {
                    content.trimStart().startsWith("{") -> content
                    else -> yamlToJson(content) ?: return Result.failure(ImportException("Failed to parse OpenAPI YAML"))
                }
            val spec = json.decodeFromString<OpenAPISpec>(normalizedJson)

            if (!spec.openapi.startsWith("3.")) {
                return Result.failure(ImportException("Unsupported OpenAPI version: ${spec.openapi}. Only 3.x is supported."))
            }

            val collection = convertSpec(spec)
            logger.info { "Successfully imported OpenAPI spec '${collection.name}' with ${countRequests(collection.items)} requests" }
            Result.success(collection)
        } catch (e: Exception) {
            logger.error(e) { "Failed to import OpenAPI specification: ${e.message}" }
            Result.failure(ImportException("Failed to parse OpenAPI specification: ${e.message}", e))
        }
    }

    private fun convertSpec(spec: OpenAPISpec): RequestCollection {
        val baseUrl = spec.servers?.firstOrNull()?.url ?: ""

        // Group operations by tags
        val operationsByTag = mutableMapOf<String, MutableList<OperationInfo>>()
        val untaggedOperations = mutableListOf<OperationInfo>()

        spec.paths?.forEach { (path, pathItem) ->
            val pathParams = pathItem.parameters ?: emptyList()

            listOf(
                "get" to pathItem.get,
                "post" to pathItem.post,
                "put" to pathItem.put,
                "delete" to pathItem.delete,
                "patch" to pathItem.patch,
                "head" to pathItem.head,
                "options" to pathItem.options,
            ).forEach { (method, operation) ->
                if (operation != null) {
                    val opInfo =
                        OperationInfo(
                            path = path,
                            method = method.uppercase(),
                            operation = operation,
                            pathLevelParams = pathParams,
                        )

                    val tags = operation.tags
                    if (tags.isNullOrEmpty()) {
                        untaggedOperations.add(opInfo)
                    } else {
                        tags.forEach { tag ->
                            operationsByTag.getOrPut(tag) { mutableListOf() }.add(opInfo)
                        }
                    }
                }
            }
        }

        // Build collection items
        val items = mutableListOf<CollectionItem>()

        // Create folders for each tag
        operationsByTag.forEach { (tag, operations) ->
            val tagInfo = spec.tags?.find { it.name == tag }
            val folder =
                CollectionItem.Folder(
                    id = UUID.randomUUID().toString(),
                    name = tag,
                    description = tagInfo?.description ?: "",
                    items = operations.map { convertOperation(it, baseUrl, spec) },
                )
            items.add(folder)
        }

        // Add untagged operations at root level
        untaggedOperations.forEach { opInfo ->
            items.add(convertOperation(opInfo, baseUrl, spec))
        }

        // Create collection variables from servers
        val variables = mutableListOf<Variable>()
        variables.add(
            Variable(
                key = "baseUrl",
                value = baseUrl,
                type = VariableType.DEFAULT,
                enabled = true,
            ),
        )

        // Add server variables
        spec.servers?.firstOrNull()?.variables?.forEach { (name, variable) ->
            variables.add(
                Variable(
                    key = name,
                    value = variable.default,
                    type = VariableType.DEFAULT,
                    enabled = true,
                ),
            )
        }

        return RequestCollection(
            id = UUID.randomUUID().toString(),
            name = spec.info.title,
            description = spec.info.description ?: "",
            items = items,
            variables = variables,
            auth = extractDefaultAuth(spec),
        )
    }

    private data class OperationInfo(
        val path: String,
        val method: String,
        val operation: OpenAPIOperation,
        val pathLevelParams: List<OpenAPIParameter>,
    )

    private fun convertOperation(
        opInfo: OperationInfo,
        baseUrl: String,
        spec: OpenAPISpec,
    ): CollectionItem.Request {
        val operation = opInfo.operation
        val allParams =
            (opInfo.pathLevelParams + (operation.parameters ?: emptyList()))
                .map { resolveParameter(it, spec) }

        // Build request name
        val name =
            operation.summary
                ?: operation.operationId
                ?: "${opInfo.method} ${opInfo.path}"

        // Extract parameters by location
        val queryParams =
            allParams
                .filter { it.location == "query" }
                .map { param ->
                    KeyValue(
                        key = param.name ?: "",
                        value = extractExampleValue(param.example, param.schema),
                        enabled = param.required == true,
                        description = param.description ?: "",
                    )
                }

        val headers =
            allParams
                .filter { it.location == "header" }
                .map { param ->
                    KeyValue(
                        key = param.name ?: "",
                        value = extractExampleValue(param.example, param.schema),
                        enabled = param.required == true,
                        description = param.description ?: "",
                    )
                }

        // Build URL with path parameters as Postest variables
        var url = "{{baseUrl}}${opInfo.path}"
        allParams
            .filter { it.location == "path" }
            .forEach { param ->
                val paramName = param.name ?: ""
                val example = extractExampleValue(param.example, param.schema)
                // Replace OpenAPI path param {name} with Postest variable {{name}}
                url = url.replace("{$paramName}", example.ifEmpty { "{{$paramName}}" })
            }

        // Extract request body
        val body =
            operation.requestBody?.let { resolveRequestBody(it, spec) }?.let { requestBody ->
                convertRequestBody(requestBody, spec)
            }

        // Add Content-Type header if body exists
        val allHeaders = headers.toMutableList()
        if (body != null && !allHeaders.any { it.key.equals("Content-Type", ignoreCase = true) }) {
            val contentType =
                when (body) {
                    is RequestBody.Json -> "application/json"
                    is RequestBody.FormUrlEncoded -> "application/x-www-form-urlencoded"
                    is RequestBody.FormData -> "multipart/form-data"
                    else -> null
                }
            contentType?.let {
                allHeaders.add(0, KeyValue(key = "Content-Type", value = it, enabled = true))
            }
        }

        return CollectionItem.Request(
            id = UUID.randomUUID().toString(),
            name = name,
            request =
                HttpRequest(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    method = convertMethod(opInfo.method),
                    url = url,
                    headers = allHeaders,
                    queryParams = queryParams,
                    body = body,
                    auth = extractOperationAuth(operation, spec),
                ),
        )
    }

    private fun resolveParameter(
        param: OpenAPIParameter,
        spec: OpenAPISpec,
    ): OpenAPIParameter {
        val ref = param.ref ?: return param
        return resolveRef(ref, spec.components?.parameters) ?: param
    }

    private fun resolveRequestBody(
        body: OpenAPIRequestBody,
        spec: OpenAPISpec,
    ): OpenAPIRequestBody {
        val ref = body.ref ?: return body
        return resolveRef(ref, spec.components?.requestBodies) ?: body
    }

    private fun yamlToJson(yamlContent: String): String? =
        runCatching {
            val loadSettings = LoadSettings.builder().build()
            val loader = Load(loadSettings)
            val loaded = loader.loadFromString(yamlContent)
            val jsonElement = yamlNodeToJsonElement(loaded)
            json.encodeToString(JsonElement.serializer(), jsonElement)
        }.onFailure {
            logger.warn(it) { "Failed to parse YAML OpenAPI content" }
        }.getOrNull()

    private fun yamlNodeToJsonElement(node: Any?): JsonElement =
        when (node) {
            null -> JsonNull
            is Map<*, *> ->
                JsonObject(
                    node
                        .mapNotNull { (k, v) ->
                            (k as? String)?.let { key -> key to yamlNodeToJsonElement(v) }
                        }.toMap(),
                )
            is Iterable<*> ->
                JsonArray(node.map { yamlNodeToJsonElement(it) })
            is Boolean -> JsonPrimitive(node)
            is Number -> JsonPrimitive(node)
            is String -> JsonPrimitive(node)
            else -> JsonPrimitive(node.toString())
        }

    private inline fun <reified T> resolveRef(
        ref: String,
        components: Map<String, T>?,
    ): T? {
        // Ref format: "#/components/xxx/name"
        val parts = ref.split("/")
        if (parts.size < 4 || parts[0] != "#" || parts[1] != "components") return null
        val name = parts.last()
        return components?.get(name)
    }

    private fun convertRequestBody(
        requestBody: OpenAPIRequestBody,
        spec: OpenAPISpec,
    ): RequestBody? {
        val content = requestBody.content ?: return null

        // Prefer JSON, then form-urlencoded, then form-data
        content["application/json"]?.let { mediaType ->
            val example = extractBodyExample(mediaType, spec)
            return RequestBody.Json(formatJsonSafely(example))
        }

        content["application/x-www-form-urlencoded"]?.let { mediaType ->
            val fields = extractFormFields(mediaType, spec)
            return RequestBody.FormUrlEncoded(fields = fields)
        }

        content["multipart/form-data"]?.let { mediaType ->
            val fields = extractFormDataFields(mediaType, spec)
            return RequestBody.FormData(fields = fields)
        }

        // Fall back to first content type
        content.entries.firstOrNull()?.let { (contentType, mediaType) ->
            val example = extractBodyExample(mediaType, spec)
            return if (contentType.contains("json")) {
                RequestBody.Json(formatJsonSafely(example))
            } else {
                RequestBody.Raw(content = example, contentType = contentType)
            }
        }

        return null
    }

    private fun extractBodyExample(
        mediaType: OpenAPIMediaType,
        spec: OpenAPISpec,
    ): String {
        // Try explicit example first
        mediaType.example?.let { return jsonElementToString(it) }

        // Try examples map
        mediaType.examples
            ?.values
            ?.firstOrNull()
            ?.value
            ?.let { return jsonElementToString(it) }

        // Generate from schema
        mediaType.schema?.let { schema ->
            return generateExampleFromSchema(schema, spec)
        }

        return "{}"
    }

    private fun extractFormFields(
        mediaType: OpenAPIMediaType,
        spec: OpenAPISpec,
    ): List<KeyValue> {
        val schema = mediaType.schema ?: return emptyList()
        val properties = schema.properties ?: return emptyList()
        val required = schema.required ?: emptyList()

        return properties.map { (name, propSchema) ->
            KeyValue(
                key = name,
                value = extractExampleValue(propSchema.example, propSchema),
                enabled = name in required,
                description = propSchema.description ?: "",
            )
        }
    }

    private fun extractFormDataFields(
        mediaType: OpenAPIMediaType,
        spec: OpenAPISpec,
    ): List<FormField> {
        val schema = mediaType.schema ?: return emptyList()
        val properties = schema.properties ?: return emptyList()
        val required = schema.required ?: emptyList()

        return properties.map { (name, propSchema) ->
            val isFile = propSchema.type == "string" && propSchema.format == "binary"
            FormField(
                key = name,
                value = if (isFile) "" else extractExampleValue(propSchema.example, propSchema),
                type = if (isFile) FormFieldType.FILE else FormFieldType.TEXT,
                enabled = name in required,
            )
        }
    }

    private fun generateExampleFromSchema(
        schema: OpenAPISchema,
        spec: OpenAPISpec,
        depth: Int = 0,
    ): String {
        if (depth > 5) return "{}" // Prevent infinite recursion

        // Handle $ref
        schema.ref?.let { ref ->
            val refSchema = resolveSchemaRef(ref, spec)
            if (refSchema != null) {
                return generateExampleFromSchema(refSchema, spec, depth + 1)
            }
        }

        // Use explicit example
        schema.example?.let { return jsonElementToString(it) }

        return when (schema.type) {
            "object" -> {
                val props = schema.properties ?: return "{}"
                val obj =
                    buildJsonObject {
                        props.forEach { (name, propSchema) ->
                            val value = generateExampleJsonElement(propSchema, spec, depth + 1)
                            put(name, value)
                        }
                    }
                json.encodeToString(JsonObject.serializer(), obj)
            }
            "array" -> {
                val itemSchema = schema.items
                if (itemSchema != null) {
                    val item = generateExampleJsonElement(itemSchema, spec, depth + 1)
                    json.encodeToString(JsonArray.serializer(), JsonArray(listOf(item)))
                } else {
                    "[]"
                }
            }
            "string" -> {
                when (schema.format) {
                    "date" -> "\"2024-01-01\""
                    "date-time" -> "\"2024-01-01T00:00:00Z\""
                    "email" -> "\"user@example.com\""
                    "uri", "url" -> "\"https://example.com\""
                    "uuid" -> "\"550e8400-e29b-41d4-a716-446655440000\""
                    else -> "\"string\""
                }
            }
            "integer", "number" -> "0"
            "boolean" -> "true"
            else -> "\"\""
        }
    }

    private fun generateExampleJsonElement(
        schema: OpenAPISchema,
        spec: OpenAPISpec,
        depth: Int,
    ): JsonElement {
        if (depth > 5) return JsonPrimitive("")

        schema.ref?.let { ref ->
            val refSchema = resolveSchemaRef(ref, spec)
            if (refSchema != null) {
                return generateExampleJsonElement(refSchema, spec, depth + 1)
            }
        }

        schema.example?.let { return it }

        return when (schema.type) {
            "object" -> {
                val props = schema.properties ?: return JsonObject(emptyMap())
                buildJsonObject {
                    props.forEach { (name, propSchema) ->
                        put(name, generateExampleJsonElement(propSchema, spec, depth + 1))
                    }
                }
            }
            "array" -> {
                val itemSchema = schema.items
                if (itemSchema != null) {
                    JsonArray(listOf(generateExampleJsonElement(itemSchema, spec, depth + 1)))
                } else {
                    JsonArray(emptyList())
                }
            }
            "string" ->
                JsonPrimitive(
                    when (schema.format) {
                        "date" -> "2024-01-01"
                        "date-time" -> "2024-01-01T00:00:00Z"
                        "email" -> "user@example.com"
                        "uri", "url" -> "https://example.com"
                        "uuid" -> "550e8400-e29b-41d4-a716-446655440000"
                        else -> "string"
                    },
                )
            "integer" -> JsonPrimitive(0)
            "number" -> JsonPrimitive(0.0)
            "boolean" -> JsonPrimitive(true)
            else -> JsonPrimitive("")
        }
    }

    private fun resolveSchemaRef(
        ref: String,
        spec: OpenAPISpec,
    ): OpenAPISchema? {
        val parts = ref.split("/")
        if (parts.size < 4 || parts[0] != "#" || parts[1] != "components" || parts[2] != "schemas") return null
        val name = parts.last()
        return spec.components?.schemas?.get(name)
    }

    private fun extractExampleValue(
        example: JsonElement?,
        schema: OpenAPISchema?,
    ): String {
        example?.let { return jsonElementToString(it) }

        schema?.example?.let { return jsonElementToString(it) }
        schema?.default?.let { return jsonElementToString(it) }
        schema?.enum?.firstOrNull()?.let { return jsonElementToString(it) }

        return ""
    }

    private fun jsonElementToString(element: JsonElement): String =
        when (element) {
            is JsonPrimitive -> element.content
            else -> json.encodeToString(JsonElement.serializer(), element)
        }

    private fun extractDefaultAuth(spec: OpenAPISpec): AuthConfig? {
        // Check global security requirements
        val securityReq = spec.security?.firstOrNull() ?: return null
        val schemeName = securityReq.keys.firstOrNull() ?: return null
        val scheme = spec.components?.securitySchemes?.get(schemeName) ?: return null

        return convertSecurityScheme(scheme)
    }

    private fun extractOperationAuth(
        operation: OpenAPIOperation,
        spec: OpenAPISpec,
    ): AuthConfig? {
        val securityReq = operation.security?.firstOrNull() ?: return null
        val schemeName = securityReq.keys.firstOrNull() ?: return null
        val scheme = spec.components?.securitySchemes?.get(schemeName) ?: return null

        return convertSecurityScheme(scheme)
    }

    private fun convertSecurityScheme(scheme: OpenAPISecurityScheme): AuthConfig? =
        when (scheme.type.lowercase()) {
            "http" -> {
                when (scheme.scheme?.lowercase()) {
                    "basic" -> AuthConfig.Basic(username = "", password = "")
                    "bearer" -> AuthConfig.Bearer(token = "")
                    else -> null
                }
            }
            "apikey" -> {
                AuthConfig.ApiKey(
                    key = scheme.name ?: "",
                    value = "",
                    addTo =
                        when (scheme.location?.lowercase()) {
                            "query" -> AuthConfig.ApiKey.AddTo.QUERY_PARAM
                            else -> AuthConfig.ApiKey.AddTo.HEADER
                        },
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
}
