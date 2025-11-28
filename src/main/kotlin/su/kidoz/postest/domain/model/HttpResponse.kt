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
)

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
