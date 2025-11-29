package su.kidoz.postest.data.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.util.VariableResolver
import java.net.URI
import java.nio.file.Paths
import java.util.Base64
import io.ktor.http.HttpMethod as KtorHttpMethod

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

class RequestExecutor(
    private val client: HttpClient,
    private val variableResolver: VariableResolver = VariableResolver(),
) {
    suspend fun execute(
        request: HttpRequest,
        environment: Environment? = null,
    ): Result<HttpResponse> {
        val resolvedUrl = buildUrl(request, environment)

        // Validate URL is parseable
        val uri =
            runCatching { URI(resolvedUrl) }.getOrElse { e ->
                logger.error { "Invalid URL: $resolvedUrl - ${e.message}" }
                return Result.failure(IllegalArgumentException("Invalid URL: ${e.message}"))
            }

        val host = uri.host ?: ""
        logger.info { "Executing ${request.method} request to host: $host" }

        // Validate headers for potential injection issues - throws on security violations
        request.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { header ->
            val validationError = validateHeader(header.key, header.value, environment)
            if (validationError != null) {
                return Result.failure(SecurityException(validationError))
            }
        }

        return runCatching {
            val startTime = System.currentTimeMillis()

            val response =
                client.request(resolvedUrl) {
                    this.method = request.method.toKtorMethod()

                    // Apply headers
                    request.headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header ->
                            val headerKey = variableResolver.resolve(header.key, environment)
                            val headerValue = variableResolver.resolve(header.value, environment)
                            this.headers.append(headerKey, headerValue)
                        }

                    // Apply authentication
                    request.auth?.let { authConfig ->
                        applyAuth(authConfig, environment, this)
                    }

                    // Apply body
                    request.body?.let { requestBody ->
                        setRequestBody(requestBody, environment, this)
                    }
                }

            // Time to first byte (headers received)
            val firstByteTime = System.currentTimeMillis()
            val timeToFirstByte = firstByteTime - startTime

            // Read body and measure download time
            val bodyText = response.bodyAsText()
            val downloadEndTime = System.currentTimeMillis()
            val downloadTime = downloadEndTime - firstByteTime

            val totalTime = downloadEndTime - startTime

            val httpResponse =
                HttpResponse(
                    statusCode = response.status.value,
                    statusText = response.status.description,
                    headers = response.headers.entries().associate { it.key to it.value },
                    body = bodyText,
                    contentType = response.headers[HttpHeaders.ContentType],
                    size = bodyText.toByteArray().size.toLong(),
                    time =
                        ResponseTime(
                            total = totalTime,
                            firstByte = timeToFirstByte,
                            download = downloadTime,
                        ),
                )

            logger.info {
                "Response received: ${httpResponse.statusCode} ${httpResponse.statusText} " +
                    "in ${totalTime}ms (TTFB: ${timeToFirstByte}ms, Download: ${downloadTime}ms)"
            }
            httpResponse
        }.onFailure { error ->
            logger.error(error) { "Request failed to host: $host - ${error.message}" }
        }
    }

    private fun buildUrl(
        request: HttpRequest,
        environment: Environment?,
    ): String {
        val baseUrl = variableResolver.resolve(request.url, environment)

        val enabledParams = request.queryParams.filter { it.enabled && it.key.isNotBlank() }
        if (enabledParams.isEmpty()) return baseUrl

        val queryString =
            enabledParams.joinToString("&") { param ->
                val paramKey = variableResolver.resolve(param.key, environment).encodeURLParameter()
                val paramValue = variableResolver.resolve(param.value, environment).encodeURLParameter()
                "$paramKey=$paramValue"
            }

        return if (baseUrl.contains("?")) {
            "$baseUrl&$queryString"
        } else {
            "$baseUrl?$queryString"
        }
    }

    private fun applyAuth(
        auth: AuthConfig,
        environment: Environment?,
        builder: HttpRequestBuilder,
    ) {
        when (auth) {
            is AuthConfig.None -> {}

            is AuthConfig.Basic -> {
                val username = variableResolver.resolve(auth.username, environment)
                val password = variableResolver.resolve(auth.password, environment)
                val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                builder.headers.append(HttpHeaders.Authorization, "Basic $credentials")
            }

            is AuthConfig.Bearer -> {
                val token = variableResolver.resolve(auth.token, environment)
                builder.headers.append(HttpHeaders.Authorization, "Bearer $token")
            }

            is AuthConfig.ApiKey -> {
                val apiKey = variableResolver.resolve(auth.key, environment)
                val apiValue = variableResolver.resolve(auth.value, environment)
                when (auth.addTo) {
                    AuthConfig.ApiKey.AddTo.HEADER -> {
                        builder.headers.append(apiKey, apiValue)
                    }

                    AuthConfig.ApiKey.AddTo.QUERY_PARAM -> {
                        builder.url {
                            parameters.append(apiKey, apiValue)
                        }
                    }
                }
            }
        }
    }

    private fun setRequestBody(
        body: RequestBody,
        environment: Environment?,
        builder: HttpRequestBuilder,
    ) {
        when (body) {
            is RequestBody.None -> {}

            is RequestBody.Json -> {
                val content = variableResolver.resolve(body.content, environment)
                builder.contentType(ContentType.Application.Json)
                builder.setBody(content)
            }

            is RequestBody.Xml -> {
                val content = variableResolver.resolve(body.content, environment)
                builder.contentType(ContentType.Application.Xml)
                builder.setBody(content)
            }

            is RequestBody.FormUrlEncoded -> {
                builder.contentType(ContentType.Application.FormUrlEncoded)
                val params =
                    body.fields
                        .filter { it.enabled && it.key.isNotBlank() }
                        .map { field ->
                            val fieldKey = variableResolver.resolve(field.key, environment)
                            val fieldValue = variableResolver.resolve(field.value, environment)
                            fieldKey to fieldValue
                        }
                builder.setBody(
                    FormDataContent(
                        Parameters.build {
                            params.forEach { (paramKey, paramValue) ->
                                append(paramKey, paramValue)
                            }
                        },
                    ),
                )
            }

            is RequestBody.Raw -> {
                val content = variableResolver.resolve(body.content, environment)
                val contentTypeStr = variableResolver.resolve(body.contentType, environment).ifBlank { "text/plain" }
                val parsedContentType =
                    runCatching { ContentType.parse(contentTypeStr) }.getOrElse { ContentType.Text.Plain }
                builder.contentType(parsedContentType)
                builder.setBody(content)
            }

            is RequestBody.Binary -> {
                val filePath = variableResolver.resolve(body.filePath, environment)

                // Security check: reject paths with null bytes (path injection attack)
                if (filePath.contains('\u0000')) {
                    throw SecurityException("Binary file path contains null byte (potential path injection)")
                }

                val file = java.io.File(filePath)
                val canonical = runCatching { file.canonicalPath }.getOrNull()
                val home = Paths.get(System.getProperty("user.home")).toFile().canonicalPath

                when {
                    canonical == null -> {
                        throw IllegalArgumentException("Cannot resolve file path: $filePath")
                    }
                    !file.exists() -> {
                        throw java.io.FileNotFoundException("Binary file does not exist: $filePath")
                    }
                    !file.isFile -> {
                        throw IllegalArgumentException("Path is not a regular file: $filePath")
                    }
                    !canonical.startsWith(home) -> {
                        throw SecurityException("Binary file must be within user home directory")
                    }
                    else -> {
                        builder.contentType(ContentType.Application.OctetStream)
                        builder.setBody(file.readBytes())
                    }
                }
            }

            is RequestBody.FormData -> {
                builder.setBody(
                    MultiPartFormDataContent(
                        formData {
                            body.fields.filter { it.enabled && it.key.isNotBlank() }.forEach { field ->
                                val fieldKey = variableResolver.resolve(field.key, environment)
                                val fieldValue = variableResolver.resolve(field.value, environment)
                                append(fieldKey, fieldValue)
                            }
                        },
                    ),
                )
            }

            is RequestBody.GraphQL -> {
                val query = variableResolver.resolve(body.query, environment)
                val variablesJson =
                    body.variables
                        ?.let { variableResolver.resolve(it, environment).takeIf { resolved -> resolved.isNotBlank() } }
                        ?.let { resolved ->
                            runCatching { Json.parseToJsonElement(resolved) }
                                .getOrElse {
                                    logger.warn(it) { "Failed to parse GraphQL variables JSON, sending without variables" }
                                    null
                                }
                        }
                builder.contentType(ContentType.Application.Json)
                val graphqlBody: JsonObject =
                    buildJsonObject {
                        put("query", query)
                        variablesJson?.let { put("variables", it) }
                    }
                builder.setBody(json.encodeToString(graphqlBody))
            }
        }
    }

    /**
     * Validates header for security issues.
     * @return Error message if validation fails, null if valid
     */
    private fun validateHeader(
        key: String,
        value: String,
        environment: Environment?,
    ): String? {
        val resolvedKey = variableResolver.resolve(key, environment)
        val resolvedValue = variableResolver.resolve(value, environment)

        // Check for control characters in header name (potential header injection)
        if (resolvedKey.any { it.isISOControl() }) {
            logger.warn { "Header name contains control characters: '$resolvedKey'" }
            return "Header name '$resolvedKey' contains control characters (potential injection)"
        }

        // Check for newlines in header value (HTTP response splitting)
        if (resolvedValue.contains('\r') || resolvedValue.contains('\n')) {
            logger.warn { "Header value contains newline characters (potential header injection): '$resolvedKey'" }
            return "Header '$resolvedKey' value contains newline characters (potential HTTP response splitting)"
        }

        // Check for null bytes
        if (resolvedKey.contains('\u0000') || resolvedValue.contains('\u0000')) {
            logger.warn { "Header contains null byte: '$resolvedKey'" }
            return "Header '$resolvedKey' contains null byte (potential injection)"
        }

        return null
    }

    private fun HttpMethod.toKtorMethod(): KtorHttpMethod =
        when (this) {
            HttpMethod.GET -> KtorHttpMethod.Get
            HttpMethod.POST -> KtorHttpMethod.Post
            HttpMethod.PUT -> KtorHttpMethod.Put
            HttpMethod.PATCH -> KtorHttpMethod.Patch
            HttpMethod.DELETE -> KtorHttpMethod.Delete
            HttpMethod.HEAD -> KtorHttpMethod.Head
            HttpMethod.OPTIONS -> KtorHttpMethod.Options
        }
}
