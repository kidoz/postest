package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Live connection state for a tab whose request uses [RequestProtocol.WEBSOCKET]. */
enum class WebSocketStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

@Serializable
enum class WebSocketDirection {
    SENT,
    RECEIVED,
    SYSTEM,
}

@Serializable
data class WebSocketMessage(
    val id: String = UUID.randomUUID().toString(),
    val direction: WebSocketDirection,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val truncated: Boolean = false,
)

data class WebSocketTabState(
    val status: WebSocketStatus = WebSocketStatus.DISCONNECTED,
    val connectionId: String? = null,
    val messages: List<WebSocketMessage> = emptyList(),
    val error: String? = null,
    val autoReconnect: Boolean = false,
)

/** Keeps the in-memory transcript bounded for long-lived connections. */
const val MAX_WEBSOCKET_MESSAGES: Int = 500

/** Maximum text retained for a single sent or received message. */
const val MAX_WEBSOCKET_MESSAGE_BYTES: Int = 1_000_000

/** Maximum binary frame size accepted for sending or receiving. */
const val MAX_WEBSOCKET_BINARY_BYTES: Int = 10 * 1024 * 1024

/** Transcript budget written to workspace snapshots. */
const val MAX_PERSISTED_WEBSOCKET_TRANSCRIPT_BYTES: Int = 1_000_000

const val MAX_PERSISTED_WEBSOCKET_MESSAGES: Int = 200

fun truncateWebSocketMessage(
    content: String,
    maxBytes: Int = MAX_WEBSOCKET_MESSAGE_BYTES,
): Pair<String, Boolean> {
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return content to false
    return bytes.copyOf(maxBytes).toString(Charsets.UTF_8) to true
}

/** Keeps the newest messages within both the count and byte budgets. */
fun List<WebSocketMessage>.limitTranscriptForStorage(
    maxBytes: Int = MAX_PERSISTED_WEBSOCKET_TRANSCRIPT_BYTES,
    maxMessages: Int = MAX_PERSISTED_WEBSOCKET_MESSAGES,
): List<WebSocketMessage> {
    if (isEmpty() || maxBytes <= 0 || maxMessages <= 0) return emptyList()
    val kept = ArrayDeque<WebSocketMessage>()
    var remaining = maxBytes
    for (message in asReversed()) {
        if (kept.size >= maxMessages || remaining <= 0) break
        val bytes = message.content.toByteArray(Charsets.UTF_8)
        val allowed = minOf(bytes.size, remaining)
        val content =
            if (allowed < bytes.size) {
                bytes.copyOf(allowed).toString(Charsets.UTF_8)
            } else {
                message.content
            }
        kept.addFirst(message.copy(content = content, truncated = message.truncated || allowed < bytes.size))
        remaining -= allowed
    }
    return kept.toList()
}
