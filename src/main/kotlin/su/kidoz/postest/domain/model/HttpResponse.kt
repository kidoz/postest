package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HttpResponse(
    val statusCode: Int,
    val statusText: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val contentType: String?,
    val size: Long,
    val time: ResponseTime,
    val truncated: Boolean = false,
)

/** Maximum response body retained in history and workspace snapshots. */
const val MAX_PERSISTED_RESPONSE_BYTES: Int = 1_000_000

/** Returns a copy whose body is capped for local persistence. */
fun HttpResponse.limitBodyForStorage(maxBytes: Int = MAX_PERSISTED_RESPONSE_BYTES): HttpResponse {
    val bytes = body.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return this
    return copy(
        body = bytes.copyOf(maxBytes).toString(Charsets.UTF_8),
        truncated = true,
    )
}

@Serializable
data class ResponseTime(
    val total: Long,
    val dns: Long? = null,
    val connect: Long? = null,
    val tlsHandshake: Long? = null,
    val firstByte: Long? = null,
    val download: Long? = null,
)

fun HttpResponse.isSuccess(): Boolean = statusCode in 200..299

fun HttpResponse.isRedirect(): Boolean = statusCode in 300..399

fun HttpResponse.isClientError(): Boolean = statusCode in 400..499

fun HttpResponse.isServerError(): Boolean = statusCode in 500..599
