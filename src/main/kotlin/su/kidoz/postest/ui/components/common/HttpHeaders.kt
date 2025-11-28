package su.kidoz.postest.ui.components.common

object HttpHeaders {
    val commonHeaders: List<String> =
        listOf(
            "Accept",
            "Accept-Charset",
            "Accept-Encoding",
            "Accept-Language",
            "Authorization",
            "Cache-Control",
            "Connection",
            "Content-Disposition",
            "Content-Encoding",
            "Content-Language",
            "Content-Length",
            "Content-Type",
            "Cookie",
            "Date",
            "ETag",
            "Expect",
            "Expires",
            "From",
            "Host",
            "If-Match",
            "If-Modified-Since",
            "If-None-Match",
            "If-Range",
            "If-Unmodified-Since",
            "Keep-Alive",
            "Last-Modified",
            "Location",
            "Max-Forwards",
            "Origin",
            "Pragma",
            "Proxy-Authorization",
            "Range",
            "Referer",
            "Retry-After",
            "Server",
            "Set-Cookie",
            "TE",
            "Trailer",
            "Transfer-Encoding",
            "Upgrade",
            "User-Agent",
            "Vary",
            "Via",
            "Warning",
            "WWW-Authenticate",
            "X-Api-Key",
            "X-Correlation-Id",
            "X-Forwarded-For",
            "X-Forwarded-Host",
            "X-Forwarded-Proto",
            "X-Request-Id",
            "X-Requested-With",
        )

    val headerValues: Map<String, List<String>> =
        mapOf(
            "Accept" to
                listOf(
                    "*/*",
                    "application/json",
                    "application/xml",
                    "application/x-www-form-urlencoded",
                    "text/html",
                    "text/plain",
                    "text/xml",
                    "image/png",
                    "image/jpeg",
                    "image/gif",
                    "multipart/form-data",
                ),
            "Accept-Charset" to
                listOf(
                    "utf-8",
                    "iso-8859-1",
                    "us-ascii",
                    "*",
                ),
            "Accept-Encoding" to
                listOf(
                    "gzip",
                    "deflate",
                    "br",
                    "identity",
                    "gzip, deflate",
                    "gzip, deflate, br",
                    "*",
                ),
            "Accept-Language" to
                listOf(
                    "en",
                    "en-US",
                    "en-GB",
                    "ru",
                    "ru-RU",
                    "de",
                    "de-DE",
                    "fr",
                    "fr-FR",
                    "es",
                    "es-ES",
                    "zh-CN",
                    "ja",
                    "*",
                ),
            "Authorization" to
                listOf(
                    "Bearer ",
                    "Basic ",
                    "Digest ",
                    "OAuth ",
                    "AWS4-HMAC-SHA256 ",
                ),
            "Cache-Control" to
                listOf(
                    "no-cache",
                    "no-store",
                    "max-age=0",
                    "max-age=3600",
                    "max-age=86400",
                    "must-revalidate",
                    "public",
                    "private",
                    "no-transform",
                    "only-if-cached",
                ),
            "Connection" to
                listOf(
                    "keep-alive",
                    "close",
                    "upgrade",
                ),
            "Content-Encoding" to
                listOf(
                    "gzip",
                    "deflate",
                    "br",
                    "identity",
                ),
            "Content-Type" to
                listOf(
                    "application/json",
                    "application/json; charset=utf-8",
                    "application/xml",
                    "application/x-www-form-urlencoded",
                    "application/octet-stream",
                    "application/pdf",
                    "application/zip",
                    "multipart/form-data",
                    "text/plain",
                    "text/plain; charset=utf-8",
                    "text/html",
                    "text/html; charset=utf-8",
                    "text/xml",
                    "text/css",
                    "text/javascript",
                    "image/png",
                    "image/jpeg",
                    "image/gif",
                    "image/svg+xml",
                    "image/webp",
                ),
            "Pragma" to
                listOf(
                    "no-cache",
                ),
            "Transfer-Encoding" to
                listOf(
                    "chunked",
                    "compress",
                    "deflate",
                    "gzip",
                    "identity",
                ),
            "User-Agent" to
                listOf(
                    "Postest/1.0",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
                    "curl/7.68.0",
                    "PostmanRuntime/7.29.0",
                ),
            "X-Requested-With" to
                listOf(
                    "XMLHttpRequest",
                ),
        )

    fun getSuggestionsForHeader(headerName: String): List<String> {
        val normalizedName = headerName.lowercase()
        return headerValues.entries
            .firstOrNull { it.key.lowercase() == normalizedName }
            ?.value
            ?: emptyList()
    }

    fun filterHeaders(query: String): List<String> {
        if (query.isBlank()) return commonHeaders
        val lowerQuery = query.lowercase()
        return commonHeaders.filter { it.lowercase().contains(lowerQuery) }
    }

    fun filterValues(
        headerName: String,
        query: String,
    ): List<String> {
        val suggestions = getSuggestionsForHeader(headerName)
        if (query.isBlank()) return suggestions
        val lowerQuery = query.lowercase()
        return suggestions.filter { it.lowercase().contains(lowerQuery) }
    }
}
