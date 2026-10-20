package su.kidoz.postest.domain.model

import java.util.UUID

/** Live connection state for a tab whose request uses [RequestProtocol.WEBSOCKET]. */
enum class WebSocketStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

enum class WebSocketDirection {
    SENT,
    RECEIVED,
    SYSTEM,
}

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
)

/** Keeps the in-memory transcript bounded for long-lived connections. */
const val MAX_WEBSOCKET_MESSAGES: Int = 500

/** Maximum text retained for a single sent or received message. */
const val MAX_WEBSOCKET_MESSAGE_BYTES: Int = 1_000_000

fun truncateWebSocketMessage(
    content: String,
    maxBytes: Int = MAX_WEBSOCKET_MESSAGE_BYTES,
): Pair<String, Boolean> {
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return content to false
    return bytes.copyOf(maxBytes).toString(Charsets.UTF_8) to true
}
