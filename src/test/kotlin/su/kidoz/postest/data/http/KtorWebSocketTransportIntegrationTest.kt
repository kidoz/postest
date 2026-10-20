package su.kidoz.postest.data.http

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.port.WebSocketEvent
import su.kidoz.postest.util.VariableResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorWebSocketTransportIntegrationTest {
    @Test
    fun `connects sends text and receives echoed frames`() =
        runBlocking {
            val server =
                embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                    install(WebSockets)
                    routing {
                        webSocket("/socket") {
                            send(Frame.Text("welcome"))
                            for (frame in incoming) {
                                if (frame is Frame.Text) send(Frame.Text("echo:${frame.readText()}"))
                            }
                        }
                    }
                }.start(wait = false)
            val client = HttpClientFactory.create()
            val port =
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            val transport = KtorWebSocketTransport(client, VariableResolver())
            val events = Channel<WebSocketEvent>(Channel.UNLIMITED)
            try {
                val connection =
                    transport
                        .connect(
                            HttpRequest(
                                protocol = RequestProtocol.WEBSOCKET,
                                url = "http://127.0.0.1:$port/socket",
                            ),
                            ExecutionContext(),
                        ).getOrThrow()
                val collector = launch { connection.events.collect { events.send(it) } }
                try {
                    val welcome = withTimeout(5_000) { events.receive() }
                    assertTrue(welcome is WebSocketEvent.Text)
                    assertEquals("welcome", welcome.content)

                    connection.send("hello")
                    val echo = withTimeout(5_000) { events.receive() }
                    assertTrue(echo is WebSocketEvent.Text)
                    assertEquals("echo:hello", echo.content)
                } finally {
                    collector.cancel()
                    connection.close()
                }
            } finally {
                client.close()
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            }
        }
}
