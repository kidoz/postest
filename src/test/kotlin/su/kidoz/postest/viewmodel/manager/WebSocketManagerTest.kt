package su.kidoz.postest.viewmodel.manager

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestFailureException
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.domain.port.WebSocketConnection
import su.kidoz.postest.domain.port.WebSocketEvent
import su.kidoz.postest.domain.usecase.ConnectWebSocketUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.TabState
import su.kidoz.postest.viewmodel.WorkspaceState
import su.kidoz.postest.viewmodel.WorkspaceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketManagerTest {
    private val request = HttpRequest(protocol = RequestProtocol.WEBSOCKET, url = "wss://example.test/socket")
    private val connectWebSocketUseCase = mockk<ConnectWebSocketUseCase>()
    private val effects = mockk<SideEffectEmitter>(relaxed = true)

    private class FakeConnection : WebSocketConnection {
        private val incoming = Channel<WebSocketEvent>(Channel.UNLIMITED)
        override val events = incoming.receiveAsFlow()
        val sent = mutableListOf<String>()
        var closed = false
        var aborted = false

        override suspend fun send(text: String) {
            sent += text
        }

        override suspend fun sendBinary(bytes: ByteArray) {
            sent += "<binary:${bytes.size}>"
        }

        override suspend fun close() {
            closed = true
        }

        override fun abort() {
            aborted = true
        }

        fun emit(event: WebSocketEvent) {
            incoming.trySend(event)
        }

        fun finish() {
            incoming.close()
        }
    }

    private fun store(): WorkspaceStore =
        WorkspaceStore(
            WorkspaceState(
                tabs = listOf(TabState(id = "tab", request = request, webSocket = WebSocketTabState())),
                activeTabId = "tab",
            ),
        )

    private fun connect(
        workspace: WorkspaceStore,
        manager: WebSocketManager,
    ) {
        manager.connect("tab", request, ExecutionContext())
    }

    @Test
    fun `connect publishes connected status and receives messages`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)

            connect(workspace, manager)
            runCurrent()

            assertEquals(
                WebSocketStatus.CONNECTED,
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.status,
            )
            connection.emit(WebSocketEvent.Text("hello"))
            runCurrent()
            val message =
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.messages
                    ?.last()
            assertEquals(WebSocketDirection.RECEIVED, message?.direction)
            assertEquals("hello", message?.content)
            manager.cleanup()
        }

    @Test
    fun `failed connection reports a fixed message and clears the attempt`() =
        runTest {
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns
                Result.failure(RequestFailureException(RequestFailure.HOST_NOT_FOUND))
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)

            connect(workspace, manager)
            runCurrent()

            val state =
                workspace.state.value.activeTab
                    ?.webSocket
            assertEquals(WebSocketStatus.DISCONNECTED, state?.status)
            assertEquals("Host not found", state?.error)
            assertNull(state?.connectionId)
            verify { effects.emit(AppSideEffect.ShowError("Host not found")) }
        }

    @Test
    fun `send appends a sent message and requires a live connection`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)
            connect(workspace, manager)
            runCurrent()

            manager.sendMessage("tab", "ping")
            runCurrent()

            assertEquals(listOf("ping"), connection.sent)
            val message =
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.messages
                    ?.last()
            assertEquals(WebSocketDirection.SENT, message?.direction)
            assertEquals("ping", message?.content)

            manager.disconnect("tab")
            runCurrent()
            manager.sendMessage("tab", "after close")
            runCurrent()

            verify { effects.emit(AppSideEffect.ShowError("WebSocket is not connected")) }
            assertEquals(1, connection.sent.size)
        }

    @Test
    fun `disconnect closes the connection and marks the tab disconnected`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)
            connect(workspace, manager)
            runCurrent()

            manager.disconnect("tab")
            runCurrent()

            assertTrue(connection.closed)
            assertEquals(
                WebSocketStatus.DISCONNECTED,
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.status,
            )
            assertEquals(
                "Disconnected",
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.messages
                    ?.last()
                    ?.content,
            )
        }

    @Test
    fun `switching a tab back to HTTP closes its connection`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)
            connect(workspace, manager)
            runCurrent()
            workspace.dispatch(
                su.kidoz.postest.viewmodel.WorkspaceCommand.EditRequest(
                    "tab",
                    request.copy(protocol = RequestProtocol.HTTP),
                ),
            )

            manager.syncTabs(emptySet())
            runCurrent()

            assertTrue(connection.closed)
        }

    @Test
    fun `a second connect is ignored while connected`() =
        runTest {
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(FakeConnection())
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)

            connect(workspace, manager)
            runCurrent()
            connect(workspace, manager)
            runCurrent()

            coVerify(exactly = 1) { connectWebSocketUseCase.connect(any(), any()) }
            manager.cleanup()
        }

    @Test
    fun `binary messages are sent and recorded`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)
            connect(workspace, manager)
            runCurrent()

            manager.sendBinary("tab", byteArrayOf(1, 2, 3))
            runCurrent()

            assertEquals(listOf("<binary:3>"), connection.sent)
            assertEquals(
                "Binary message sent (3 bytes)",
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.messages
                    ?.last()
                    ?.content,
            )
            manager.cleanup()
        }

    @Test
    fun `auto reconnect retries after an unexpected close`() =
        runTest {
            val first = FakeConnection()
            val second = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returnsMany
                listOf(Result.success(first), Result.success(second))
            val workspace = store()
            val manager =
                WebSocketManager(
                    connectWebSocketUseCase,
                    workspace,
                    effects,
                    this,
                    reconnectDelayMillis = { 0 },
                )
            manager.setAutoReconnect("tab", true)
            connect(workspace, manager)
            advanceUntilIdle()

            first.finish()
            advanceUntilIdle()

            coVerify(exactly = 2) { connectWebSocketUseCase.connect(any(), any()) }
            assertEquals(
                WebSocketStatus.CONNECTED,
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.status,
            )
            val contents =
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.messages
                    ?.map { it.content }
                    .orEmpty()
            assertTrue(contents.any { it.startsWith("Reconnecting") })
            assertTrue(contents.contains("Reconnected"))
            manager.cleanup()
        }

    @Test
    fun `auto reconnect does not run unless enabled`() =
        runTest {
            val connection = FakeConnection()
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns Result.success(connection)
            val workspace = store()
            val manager = WebSocketManager(connectWebSocketUseCase, workspace, effects, this)
            connect(workspace, manager)
            advanceUntilIdle()

            connection.finish()
            advanceUntilIdle()

            coVerify(exactly = 1) { connectWebSocketUseCase.connect(any(), any()) }
            assertEquals(
                WebSocketStatus.DISCONNECTED,
                workspace.state.value.activeTab
                    ?.webSocket
                    ?.status,
            )
            manager.cleanup()
        }
}
