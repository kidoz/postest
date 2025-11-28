package su.kidoz.postest.data.import

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for PostmanUrl that handles both string and object formats.
 * In Postman collections, URL can be either:
 * - A simple string: "https://api.example.com/users"
 * - An object with raw, protocol, host, path, query, etc.
 */
object PostmanUrlSerializer : KSerializer<PostmanUrl> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PostmanUrl")

    override fun deserialize(decoder: Decoder): PostmanUrl {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw IllegalStateException("This serializer can only be used with JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                // URL is a simple string
                PostmanUrl(raw = element.content)
            }
            is JsonObject -> {
                // URL is an object
                val json = Json { ignoreUnknownKeys = true }
                PostmanUrl(
                    raw = element["raw"]?.jsonPrimitive?.contentOrNull,
                    protocol = element["protocol"]?.jsonPrimitive?.contentOrNull,
                    host =
                        element["host"]?.let { hostElement ->
                            when (hostElement) {
                                is JsonArray -> hostElement.map { it.jsonPrimitive.content }
                                is JsonPrimitive -> listOf(hostElement.content)
                                else -> null
                            }
                        },
                    path =
                        element["path"]?.let { pathElement ->
                            when (pathElement) {
                                is JsonArray -> pathElement.map { it.jsonPrimitive.content }
                                is JsonPrimitive -> pathElement.content.split("/")
                                else -> null
                            }
                        },
                    query =
                        element["query"]?.let { queryElement ->
                            if (queryElement is JsonArray) {
                                queryElement.map { json.decodeFromJsonElement<PostmanQueryParam>(it) }
                            } else {
                                null
                            }
                        },
                    variable =
                        element["variable"]?.let { variableElement ->
                            if (variableElement is JsonArray) {
                                variableElement.map { json.decodeFromJsonElement<PostmanVariable>(it) }
                            } else {
                                null
                            }
                        },
                )
            }
            else -> PostmanUrl()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: PostmanUrl,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw IllegalStateException("This serializer can only be used with JSON")

        val jsonObject =
            buildJsonObject {
                value.raw?.let { put("raw", it) }
                value.protocol?.let { put("protocol", it) }
                value.host?.let { put("host", JsonArray(it.map { h -> JsonPrimitive(h) })) }
                value.path?.let { put("path", JsonArray(it.map { p -> JsonPrimitive(p) })) }
                value.query?.let { queryList ->
                    put(
                        "query",
                        JsonArray(
                            queryList.map { q ->
                                buildJsonObject {
                                    q.key?.let { put("key", it) }
                                    q.value?.let { put("value", it) }
                                    q.description?.let { put("description", it) }
                                    q.disabled?.let { put("disabled", it) }
                                }
                            },
                        ),
                    )
                }
            }
        jsonEncoder.encodeJsonElement(jsonObject)
    }
}
