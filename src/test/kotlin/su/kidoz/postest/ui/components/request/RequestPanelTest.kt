package su.kidoz.postest.ui.components.request

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RequestPanelTest {
    @Test
    fun `selecting WebSocket switches the panel to connection controls`() =
        runComposeUiTest {
            var request by mutableStateOf(HttpRequest(url = "wss://example.test/socket"))
            var connected = false
            setContent {
                AppTheme {
                    RequestPanel(
                        request = request,
                        onRequestChange = { request = it },
                        onSend = {},
                        isLoading = false,
                        webSocketStatus = WebSocketStatus.DISCONNECTED,
                        onConnect = { connected = true },
                        onDisconnect = {},
                    )
                }
            }

            onNodeWithText("Body").assertExists()
            onNodeWithContentDescription("Select method").performClick()
            onNodeWithText("WebSocket").performClick()

            runOnIdle { assertEquals(RequestProtocol.WEBSOCKET, request.protocol) }
            onNodeWithText("Body").assertDoesNotExist()
            onNodeWithText("Connect").assertExists().performClick()
            runOnIdle { assertTrue(connected) }
        }
}
