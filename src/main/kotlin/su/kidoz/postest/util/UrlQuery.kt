package su.kidoz.postest.util

import su.kidoz.postest.domain.model.KeyValue
import java.net.URLDecoder

/** Keeps duplicate parameters and decodes values once; fragments are never query data. */
fun parseUrlQuery(url: String): List<KeyValue> {
    val query = url.substringBefore('#').substringAfter('?', "")
    return query.split('&').filter { it.isNotBlank() }.map { pair ->
        KeyValue(
            key = decodeQueryComponent(pair.substringBefore('=')),
            value = decodeQueryComponent(pair.substringAfter('=', "")),
        )
    }
}

private fun decodeQueryComponent(value: String): String = runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)
