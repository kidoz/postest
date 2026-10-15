package su.kidoz.postest.util

import java.net.URI

/** Labels unnamed requests without exposing URL credentials, queries, or fragments. */
fun requestDisplayName(
    name: String,
    url: String,
): String {
    if (name.isNotBlank()) return name
    val candidate = url.trim().let { if (it.startsWith("{{")) it.substringAfter("}}", "") else it }
    val path =
        runCatching {
            val uri = URI(candidate.replace("{", "%7B").replace("}", "%7D"))
            when {
                uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true) -> {
                    uri.path.ifBlank { "/" }
                }

                candidate.startsWith("/") && !candidate.startsWith("//") -> {
                    uri.path
                }

                else -> {
                    ""
                }
            }
        }.getOrDefault("")
    return path.ifBlank { "Untitled Request" }
}
