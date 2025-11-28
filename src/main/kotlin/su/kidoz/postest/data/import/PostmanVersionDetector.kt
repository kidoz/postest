package su.kidoz.postest.data.import

import kotlinx.serialization.json.*

/**
 * Detects the version of a Postman collection from its JSON content.
 */
object PostmanVersionDetector {
    /**
     * Postman collection versions
     */
    enum class PostmanVersion {
        V1, // v1.0.0 - uses 'requests' and 'folders' arrays
        V2_0, // v2.0.0 - uses 'item' array
        V2_1, // v2.1.0 - uses 'item' array (minor differences from v2.0)
        UNKNOWN,
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Detect the version of a Postman collection from JSON string.
     */
    fun detectVersion(jsonString: String): PostmanVersion {
        return try {
            val jsonElement = json.parseToJsonElement(jsonString)
            if (jsonElement !is JsonObject) return PostmanVersion.UNKNOWN

            detectVersionFromObject(jsonElement)
        } catch (e: Exception) {
            PostmanVersion.UNKNOWN
        }
    }

    /**
     * Detect version from a parsed JSON object.
     */
    fun detectVersionFromObject(jsonObject: JsonObject): PostmanVersion {
        // Check for schema URL in info block (v2.x)
        val info = jsonObject["info"]
        if (info is JsonObject) {
            val schema = info["schema"]?.jsonPrimitive?.contentOrNull
            if (schema != null) {
                return when {
                    schema.contains("v2.1.0") -> PostmanVersion.V2_1
                    schema.contains("v2.0.0") -> PostmanVersion.V2_0
                    schema.contains("v1.0.0") -> PostmanVersion.V1
                    // Default to v2.1 if schema URL contains postman but version is unclear
                    schema.contains("postman") -> PostmanVersion.V2_1
                    else -> detectByStructure(jsonObject)
                }
            }
        }

        // Fall back to structure-based detection
        return detectByStructure(jsonObject)
    }

    /**
     * Detect version based on the structure of the JSON object.
     */
    private fun detectByStructure(jsonObject: JsonObject): PostmanVersion {
        // v1.0.0 characteristics:
        // - Has 'requests' array at root level
        // - Has 'folders' array at root level
        // - Has 'order' array at root level
        // - Has 'id' at root level (not inside 'info')
        val hasRequests = jsonObject.containsKey("requests")
        val hasOrder = jsonObject.containsKey("order")

        if (hasRequests || (hasOrder && !jsonObject.containsKey("item"))) {
            return PostmanVersion.V1
        }

        // v2.x characteristics:
        // - Has 'info' object with 'name'
        // - Has 'item' array instead of 'requests'
        val hasInfo = jsonObject.containsKey("info")
        val hasItem = jsonObject.containsKey("item")

        if (hasInfo && hasItem) {
            // Try to distinguish between v2.0 and v2.1
            // v2.1 has some additional features but for practical purposes they're compatible
            // Default to v2.1 as it's the most recent
            return PostmanVersion.V2_1
        }

        // If we have 'info' but no 'item', might be empty v2.x collection
        if (hasInfo) {
            return PostmanVersion.V2_1
        }

        // Legacy detection: if it has 'id' and 'name' at root with 'requests', it's v1
        if (jsonObject.containsKey("id") && jsonObject.containsKey("name")) {
            return PostmanVersion.V1
        }

        return PostmanVersion.UNKNOWN
    }

    /**
     * Check if the JSON string appears to be a Postman collection.
     */
    fun isPostmanCollection(jsonString: String): Boolean {
        return try {
            val trimmed = jsonString.trim()
            if (!trimmed.startsWith("{")) return false

            val jsonElement = json.parseToJsonElement(trimmed)
            if (jsonElement !is JsonObject) return false

            // Check for v2.x structure
            val hasInfo = jsonElement.containsKey("info")
            val hasItem = jsonElement.containsKey("item")

            // Check for v1.x structure
            val hasRequests = jsonElement.containsKey("requests")
            val hasV1Structure =
                jsonElement.containsKey("id") &&
                    jsonElement.containsKey("name") &&
                    (hasRequests || jsonElement.containsKey("order"))

            // Check for Postman schema URL
            val hasPostmanSchema =
                jsonString.contains("schema.getpostman.com") ||
                    jsonString.contains("schema.postman.com")

            hasPostmanSchema || (hasInfo && hasItem) || hasV1Structure
        } catch (e: Exception) {
            false
        }
    }
}
