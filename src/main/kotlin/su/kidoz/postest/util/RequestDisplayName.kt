package su.kidoz.postest.util

import java.net.URI

/** Labels unnamed requests without exposing URL credentials, queries, or fragments. */
fun requestDisplayName(
    name: String,
    url: String,
): String {
    if (name.isNotBlank() && name != DEFAULT_REQUEST_NAME) return name

    val trimmed = url.trim()
    if (VARIABLE_ONLY_URL.matches(trimmed)) return trimmed.removeSuffix("/")

    val candidate = trimmed.let { if (it.startsWith("{{")) it.substringAfter("}}", "") else it }
    val label =
        runCatching {
            val uri = URI(candidate.replace("{", "%7B").replace("}", "%7D"))
            when {
                uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) -> {
                    val path = uri.path
                    if (path.isBlank() || path == "/") hostWithPort(uri) ?: "/" else path
                }

                candidate.startsWith("/") && !candidate.startsWith("//") -> {
                    uri.path
                }

                else -> {
                    ""
                }
            }
        }.getOrDefault("")
    return label.ifBlank { DEFAULT_REQUEST_NAME }
}

private fun hostWithPort(uri: URI): String? {
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    return if (uri.port != -1) "$host:${uri.port}" else host
}

private val VARIABLE_ONLY_URL = Regex("""^\{\{[^}]+\}\}/?$""")

private const val DEFAULT_REQUEST_NAME = "Untitled Request"
