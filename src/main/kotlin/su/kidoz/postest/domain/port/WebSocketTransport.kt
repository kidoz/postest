package su.kidoz.postest.domain.port

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest

/** Opens a WebSocket connection. Expected failures are values; cancellation always propagates. */
interface WebSocketTransport {
    suspend fun connect(
        request: HttpRequest,
        context: ExecutionContext,
    ): Result<WebSocketConnection>
}

interface WebSocketConnection {
    val events: Flow<WebSocketEvent>

    suspend fun send(text: String)

    suspend fun close()

    /** Releases the connection without suspending; used during shutdown. */
    fun abort()
}

sealed interface WebSocketEvent {
    data class Text(
        val content: String,
    ) : WebSocketEvent

    data class Binary(
        val size: Int,
    ) : WebSocketEvent

    data class Closed(
        val reason: String?,
    ) : WebSocketEvent
}
