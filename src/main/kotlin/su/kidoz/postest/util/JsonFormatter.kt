package su.kidoz.postest.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val logger = KotlinLogging.logger {}

object JsonFormatter {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    fun format(jsonString: String): String =
        try {
            val element = json.parseToJsonElement(jsonString)
            json.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            logger.debug(e) { "Failed to format JSON: ${e.message}" }
            jsonString
        }

    fun minify(jsonString: String): String =
        try {
            val element = Json.parseToJsonElement(jsonString)
            Json.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            logger.debug(e) { "Failed to minify JSON: ${e.message}" }
            jsonString
        }

    fun isValid(jsonString: String): Boolean =
        try {
            Json.parseToJsonElement(jsonString)
            true
        } catch (e: Exception) {
            logger.debug(e) { "Invalid JSON: ${e.message}" }
            false
        }
}
