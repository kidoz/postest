package su.kidoz.postest.data.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.ClientCertConfig
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestOutcome
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.port.RequestTransport
import su.kidoz.postest.util.VariableResolver
import java.net.URI
import java.nio.file.Paths
import java.util.Base64
import io.ktor.client.statement.HttpResponse as KtorHttpResponse
import io.ktor.http.HttpMethod as KtorHttpMethod

private val logger = KotlinLogging.logger {}
private val json = Json { ignoreUnknownKeys = true }

private const val MAX_RESPONSE_BYTES = 10L * 1024 * 1024

private data class ResolutionContext(
    val environment: Environment?,
    val collectionVariables: List<Variable>,
)

class RequestExecutor(
    private val client: HttpClient,
    private val variableResolver: VariableResolver = VariableResolver(),
) : RequestTransport {
    private fun ResolutionContext.resolve(value: String): String = variableResolver.resolve(value, environment, collectionVariables)

    override suspend fun executeRequest(
        request: HttpRequest,
        context: ExecutionContext,
    ): RequestOutcome =
        execute(request, context.environment, context.collectionVariables).fold(
            onSuccess = { RequestOutcome.Success(it) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                val failure =
                    when (error) {
                        is SecurityException -> {
                            RequestFailure.BLOCKED
                        }

                        is IllegalArgumentException -> {
                            RequestFailure.INVALID_REQUEST
                        }

                        is java.net.UnknownHostException -> {
                            RequestFailure.HOST_NOT_FOUND
                        }

                        is java.net.ConnectException -> {
                            RequestFailure.CONNECTION_FAILED
                        }

                        is java.net.SocketTimeoutException, is io.ktor.client.plugins.HttpRequestTimeoutException -> {
                            RequestFailure.TIMEOUT
                        }

                        is javax.net.ssl.SSLException -> {
                            RequestFailure.TLS
                        }

                        else -> {
                            RequestFailure.NETWORK
                        }
                    }
                RequestOutcome.Failure(failure)
            },
        )

    suspend fun execute(
        request: HttpRequest,
        environment: Environment? = null,
        collectionVariables: List<Variable> = emptyList(),
    ): Result<HttpResponse> {
        val context = ResolutionContext(environment, collectionVariables)
        val resolvedUrl = buildUrl(request, context)

        // Validate URL is parseable
        val uri =
            runCatching { URI(resolvedUrl) }.getOrElse {
                logger.warn { "Invalid URL for ${request.method} request" }
                return Result.failure(IllegalArgumentException("Invalid URL"))
            }

        val host = uri.host ?: ""
        logger.info { "Executing ${request.method} request to host: $host" }

        // Validate headers for potential injection issues - throws on security violations
        request.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { header ->
            val validationError = validateHeader(header.key, header.value, context)
            if (validationError != null) {
                return Result.failure(SecurityException(validationError))
            }
        }

        // Resolve client certificate if needed
        val certClient =
            runCatching { resolveClientCertificate(request.clientCertificate, context) }.getOrElse { e ->
                logger.error { "Failed to load client certificate (${e::class.simpleName})" }
                return Result.failure(IllegalArgumentException("Client certificate could not be loaded"))
            }

        return runCatching {
            val startTime = System.currentTimeMillis()
            val effectiveClient = certClient ?: client

            // Read binary body off the calling thread before building the request
            val binaryBody = (request.body as? RequestBody.Binary)?.let { loadBinaryBody(it, context) }
            val sensitiveHeaders = mutableSetOf<String>()

            val statement =
                effectiveClient.prepareRequest(resolvedUrl) {
                    this.method = request.method.toKtorMethod()
                    attributes.put(OriginalRequestAuthority, url.authorityKey())
                    attributes.put(SensitiveRequestHeaders, sensitiveHeaders)

                    // Apply headers
                    request.headers
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header ->
                            val headerKey = context.resolve(header.key)
                            val headerValue = context.resolve(header.value)
                            this.headers.append(headerKey, headerValue)
                        }

                    // Apply authentication
                    request.auth?.let { authConfig ->
                        applyAuth(authConfig, context, this, sensitiveHeaders)
                    }

                    // Apply body
                    request.body?.let { requestBody ->
                        val hasExplicitContentType =
                            request.headers.any {
                                it.enabled && it.key.equals(HttpHeaders.ContentType, ignoreCase = true)
                            }
                        setRequestBody(
                            requestBody,
                            context,
                            this,
                            binaryBody,
                            applyDefaultContentType = !hasExplicitContentType,
                        )
                    }
                }

            statement.execute { response -> readResponse(response, startTime) }
        }.also {
            certClient?.close()
        }.onFailure { error ->
            // Cancellation must propagate instead of surfacing as a request failure
            if (error is CancellationException) throw error
            logger.error { "Request failed for ${request.method} to host: $host (${error::class.simpleName})" }
        }
    }

    private suspend fun readResponse(
        response: KtorHttpResponse,
        startTime: Long,
    ): HttpResponse {
        // Time to first byte (headers received)
        val firstByteTime = System.currentTimeMillis()
        val timeToFirstByte = firstByteTime - startTime

        // Read body with a hard limit so a large response cannot exhaust memory
        val bodyPacket = response.bodyAsChannel().readBuffer(MAX_RESPONSE_BYTES + 1)
        val receivedBytes = bodyPacket.readByteArray()
        val truncated = receivedBytes.size > MAX_RESPONSE_BYTES
        val bodyBytes =
            if (truncated) {
                receivedBytes.copyOf(MAX_RESPONSE_BYTES.toInt())
            } else {
                receivedBytes
            }
        val charset = response.contentType()?.charset() ?: Charsets.UTF_8
        val bodyText = bodyBytes.toString(charset)
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
                size = bodyBytes.size.toLong(),
                time =
                    ResponseTime(
                        total = totalTime,
                        firstByte = timeToFirstByte,
                        download = downloadTime,
                    ),
                truncated = truncated,
            )

        logger.info {
            "Response received: ${httpResponse.statusCode} ${httpResponse.statusText} " +
                "in ${totalTime}ms (TTFB: ${timeToFirstByte}ms, Download: ${downloadTime}ms)"
        }
        return httpResponse
    }

    private suspend fun resolveClientCertificate(
        certConfig: ClientCertConfig?,
        context: ResolutionContext,
    ): HttpClient? {
        if (certConfig == null) return null

        val certPath = context.resolve(certConfig.certPath)
        val keyPath = context.resolve(certConfig.keyPath)
        val passphrase = context.resolve(certConfig.passphrase)

        logger.info { "Loading client certificate for mTLS" }
        val certAndKey = withContext(Dispatchers.IO) { CertificateLoader.load(certPath, keyPath, passphrase) }
        return HttpClientFactory.createWithClientCertificate(certAndKey)
    }

    private fun buildUrl(
        request: HttpRequest,
        context: ResolutionContext,
    ): String {
        val baseUrl = context.resolve(request.url)

        val enabledParams = request.queryParams.filter { it.enabled && it.key.isNotBlank() }
        if (enabledParams.isEmpty()) return baseUrl

        val queryString =
            enabledParams.joinToString("&") { param ->
                val paramKey = context.resolve(param.key).encodeURLParameter()
                val paramValue = context.resolve(param.value).encodeURLParameter()
                "$paramKey=$paramValue"
            }

        val fragmentIndex = baseUrl.indexOf('#')
        val urlWithoutFragment = if (fragmentIndex >= 0) baseUrl.substring(0, fragmentIndex) else baseUrl
        val fragment = if (fragmentIndex >= 0) baseUrl.substring(fragmentIndex) else ""
        val separator =
            when {
                urlWithoutFragment.endsWith("?") || urlWithoutFragment.endsWith("&") -> ""
                urlWithoutFragment.contains("?") -> "&"
                else -> "?"
            }
        return urlWithoutFragment + separator + queryString + fragment
    }

    private fun applyAuth(
        auth: AuthConfig,
        context: ResolutionContext,
        builder: HttpRequestBuilder,
        sensitiveHeaders: MutableSet<String>,
    ) {
        when (auth) {
            is AuthConfig.None -> {}

            is AuthConfig.Basic -> {
                val username = context.resolve(auth.username)
                val password = context.resolve(auth.password)
                val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                builder.headers.append(HttpHeaders.Authorization, "Basic $credentials")
            }

            is AuthConfig.Bearer -> {
                val token = context.resolve(auth.token)
                builder.headers.append(HttpHeaders.Authorization, "Bearer $token")
            }

            is AuthConfig.ApiKey -> {
                val apiKey = context.resolve(auth.key)
                val apiValue = context.resolve(auth.value)
                when (auth.addTo) {
                    AuthConfig.ApiKey.AddTo.HEADER -> {
                        builder.headers.append(apiKey, apiValue)
                        sensitiveHeaders += apiKey
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
        context: ResolutionContext,
        builder: HttpRequestBuilder,
        binaryBody: ByteArray? = null,
        applyDefaultContentType: Boolean = true,
    ) {
        fun applyContentType(type: ContentType) {
            if (applyDefaultContentType) builder.contentType(type)
        }

        when (body) {
            is RequestBody.None -> {}

            is RequestBody.Json -> {
                val content = context.resolve(body.content)
                applyContentType(ContentType.Application.Json)
                builder.setBody(content)
            }

            is RequestBody.Xml -> {
                val content = context.resolve(body.content)
                applyContentType(ContentType.Application.Xml)
                builder.setBody(content)
            }

            is RequestBody.FormUrlEncoded -> {
                applyContentType(ContentType.Application.FormUrlEncoded)
                val params =
                    body.fields
                        .filter { it.enabled && it.key.isNotBlank() }
                        .map { field ->
                            val fieldKey = context.resolve(field.key)
                            val fieldValue = context.resolve(field.value)
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
                val content = context.resolve(body.content)
                val contentTypeStr = context.resolve(body.contentType).ifBlank { "text/plain" }
                val parsedContentType =
                    runCatching { ContentType.parse(contentTypeStr) }.getOrElse { ContentType.Text.Plain }
                applyContentType(parsedContentType)
                builder.setBody(content)
            }

            is RequestBody.Binary -> {
                applyContentType(ContentType.Application.OctetStream)
                builder.setBody(binaryBody ?: throw IllegalStateException("Binary body was not loaded"))
            }

            is RequestBody.FormData -> {
                builder.setBody(
                    MultiPartFormDataContent(
                        formData {
                            body.fields.filter { it.enabled && it.key.isNotBlank() }.forEach { field ->
                                val fieldKey = context.resolve(field.key)
                                val fieldValue = context.resolve(field.value)
                                append(fieldKey, fieldValue)
                            }
                        },
                    ),
                )
            }

            is RequestBody.GraphQL -> {
                val query = context.resolve(body.query)
                val variablesJson =
                    body.variables
                        ?.let { context.resolve(it).takeIf { resolved -> resolved.isNotBlank() } }
                        ?.let { resolved ->
                            runCatching { Json.parseToJsonElement(resolved) }
                                .getOrElse {
                                    logger.warn { "Failed to parse GraphQL variables JSON, sending without variables" }
                                    null
                                }
                        }
                applyContentType(ContentType.Application.Json)
                val graphqlBody: JsonObject =
                    buildJsonObject {
                        put("query", query)
                        variablesJson?.let { put("variables", it) }
                    }
                builder.setBody(json.encodeToString(graphqlBody))
            }
        }
    }

    private suspend fun loadBinaryBody(
        body: RequestBody.Binary,
        context: ResolutionContext,
    ): ByteArray {
        val filePath = context.resolve(body.filePath).trim()
        require(filePath.isNotBlank()) { "Select a file for the binary body" }

        // Security check: reject paths with null bytes (path injection attack)
        if (filePath.contains('\u0000')) {
            throw SecurityException("Binary file path contains null byte (potential path injection)")
        }

        val file = java.io.File(filePath)
        val canonical = runCatching { file.canonicalPath }.getOrNull()
        val home = Paths.get(System.getProperty("user.home")).toFile().canonicalPath

        when {
            canonical == null -> {
                throw IllegalArgumentException("Binary file path is invalid")
            }

            !file.exists() -> {
                throw java.io.FileNotFoundException("Binary file does not exist")
            }

            !file.isFile -> {
                throw IllegalArgumentException("Binary path is not a regular file")
            }

            !canonical.startsWith(home) -> {
                throw SecurityException("Binary file must be within the user home directory")
            }
        }

        return withContext(Dispatchers.IO) { file.readBytes() }
    }

    /**
     * Validates header for security issues.
     * @return Error message if validation fails, null if valid
     */
    private fun validateHeader(
        key: String,
        value: String,
        context: ResolutionContext,
    ): String? {
        val resolvedKey = context.resolve(key)
        val resolvedValue = context.resolve(value)

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
