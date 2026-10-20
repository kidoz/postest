package su.kidoz.postest.domain.usecase

import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.port.WebSocketConnection
import su.kidoz.postest.domain.port.WebSocketTransport

class ConnectWebSocketUseCase(
    private val transport: WebSocketTransport,
) {
    suspend fun connect(
        request: HttpRequest,
        context: ExecutionContext,
    ): Result<WebSocketConnection> = transport.connect(request, context)
}
