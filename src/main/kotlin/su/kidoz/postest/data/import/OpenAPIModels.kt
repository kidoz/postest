package su.kidoz.postest.data.import

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * OpenAPI 3.0/3.1 Specification data models
 * Based on: https://spec.openapis.org/oas/v3.1.0
 *
 * This is a simplified model focusing on what's needed for import:
 * - Paths and operations (GET, POST, etc.)
 * - Parameters (query, header, path)
 * - Request bodies
 * - Server URLs
 * - Security schemes
 */

@Serializable
data class OpenAPISpec(
    val openapi: String, // "3.0.0", "3.0.3", "3.1.0", etc.
    val info: OpenAPIInfo,
    val servers: List<OpenAPIServer>? = null,
    val paths: Map<String, OpenAPIPathItem>? = null,
    val components: OpenAPIComponents? = null,
    val security: List<Map<String, List<String>>>? = null,
    val tags: List<OpenAPITag>? = null,
)

@Serializable
data class OpenAPIInfo(
    val title: String,
    val description: String? = null,
    val version: String,
    val termsOfService: String? = null,
    val contact: OpenAPIContact? = null,
    val license: OpenAPILicense? = null,
)

@Serializable
data class OpenAPIContact(
    val name: String? = null,
    val url: String? = null,
    val email: String? = null,
)

@Serializable
data class OpenAPILicense(
    val name: String,
    val url: String? = null,
)

@Serializable
data class OpenAPIServer(
    val url: String,
    val description: String? = null,
    val variables: Map<String, OpenAPIServerVariable>? = null,
)

@Serializable
data class OpenAPIServerVariable(
    val default: String,
    val description: String? = null,
    val enum: List<String>? = null,
)

@Serializable
data class OpenAPIPathItem(
    val summary: String? = null,
    val description: String? = null,
    val get: OpenAPIOperation? = null,
    val post: OpenAPIOperation? = null,
    val put: OpenAPIOperation? = null,
    val delete: OpenAPIOperation? = null,
    val patch: OpenAPIOperation? = null,
    val head: OpenAPIOperation? = null,
    val options: OpenAPIOperation? = null,
    val trace: OpenAPIOperation? = null,
    val parameters: List<OpenAPIParameter>? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPIOperation(
    val operationId: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val parameters: List<OpenAPIParameter>? = null,
    val requestBody: OpenAPIRequestBody? = null,
    val responses: Map<String, OpenAPIResponse>? = null,
    val security: List<Map<String, List<String>>>? = null,
    val deprecated: Boolean? = null,
    val servers: List<OpenAPIServer>? = null,
)

@Serializable
data class OpenAPIParameter(
    val name: String? = null,
    @SerialName("in")
    val location: String? = null, // "query", "header", "path", "cookie"
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val allowEmptyValue: Boolean? = null,
    val schema: OpenAPISchema? = null,
    val example: JsonElement? = null,
    val examples: Map<String, OpenAPIExample>? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPIRequestBody(
    val description: String? = null,
    val required: Boolean? = null,
    val content: Map<String, OpenAPIMediaType>? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPIMediaType(
    val schema: OpenAPISchema? = null,
    val example: JsonElement? = null,
    val examples: Map<String, OpenAPIExample>? = null,
    val encoding: Map<String, OpenAPIEncoding>? = null,
)

@Serializable
data class OpenAPIEncoding(
    val contentType: String? = null,
    val headers: Map<String, OpenAPIHeader>? = null,
    val style: String? = null,
    val explode: Boolean? = null,
    val allowReserved: Boolean? = null,
)

@Serializable
data class OpenAPIHeader(
    val description: String? = null,
    val required: Boolean? = null,
    val deprecated: Boolean? = null,
    val schema: OpenAPISchema? = null,
    val example: JsonElement? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPIResponse(
    val description: String? = null,
    val headers: Map<String, OpenAPIHeader>? = null,
    val content: Map<String, OpenAPIMediaType>? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPISchema(
    val type: String? = null,
    val format: String? = null,
    val title: String? = null,
    val description: String? = null,
    val default: JsonElement? = null,
    val example: JsonElement? = null,
    val enum: List<JsonElement>? = null,
    val properties: Map<String, OpenAPISchema>? = null,
    val items: OpenAPISchema? = null,
    val required: List<String>? = null,
    val additionalProperties: JsonElement? = null,
    val allOf: List<OpenAPISchema>? = null,
    val oneOf: List<OpenAPISchema>? = null,
    val anyOf: List<OpenAPISchema>? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPIExample(
    val summary: String? = null,
    val description: String? = null,
    val value: JsonElement? = null,
    val externalValue: String? = null,
    @SerialName("\$ref")
    val ref: String? = null,
)

@Serializable
data class OpenAPITag(
    val name: String,
    val description: String? = null,
)

@Serializable
data class OpenAPIComponents(
    val schemas: Map<String, OpenAPISchema>? = null,
    val responses: Map<String, OpenAPIResponse>? = null,
    val parameters: Map<String, OpenAPIParameter>? = null,
    val examples: Map<String, OpenAPIExample>? = null,
    val requestBodies: Map<String, OpenAPIRequestBody>? = null,
    val headers: Map<String, OpenAPIHeader>? = null,
    val securitySchemes: Map<String, OpenAPISecurityScheme>? = null,
)

@Serializable
data class OpenAPISecurityScheme(
    val type: String, // "apiKey", "http", "oauth2", "openIdConnect"
    val description: String? = null,
    val name: String? = null, // For apiKey
    @SerialName("in")
    val location: String? = null, // For apiKey: "query", "header", "cookie"
    val scheme: String? = null, // For http: "basic", "bearer"
    val bearerFormat: String? = null, // For http bearer
    val flows: OpenAPIOAuthFlows? = null, // For oauth2
    val openIdConnectUrl: String? = null, // For openIdConnect
)

@Serializable
data class OpenAPIOAuthFlows(
    val implicit: OpenAPIOAuthFlow? = null,
    val password: OpenAPIOAuthFlow? = null,
    val clientCredentials: OpenAPIOAuthFlow? = null,
    val authorizationCode: OpenAPIOAuthFlow? = null,
)

@Serializable
data class OpenAPIOAuthFlow(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String>? = null,
)
