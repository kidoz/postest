package su.kidoz.postest.ui.components.response

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketMessage
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WebSocketPanelTest {
    @Test
    fun `messages render and the composer sends while connected`() =
        runComposeUiTest {
            var sent: String? = null
            setContent {
                AppTheme {
                    WebSocketPanel(
                        state =
                            WebSocketTabState(
                                status = WebSocketStatus.CONNECTED,
                                messages =
                                    listOf(
                                        WebSocketMessage(direction = WebSocketDirection.RECEIVED, content = "server hello"),
                                        WebSocketMessage(direction = WebSocketDirection.SENT, content = "client hello"),
                                    ),
                            ),
                        onSendMessage = { sent = it },
                        onClearMessages = {},
                    )
                }
            }

            onNodeWithText("Received").assertExists()
            onNodeWithText("Sent").assertExists()
            onNodeWithText("server hello").assertExists()
            onNodeWithText("client hello").assertExists()

            onNodeWithTag("websocket-message-input").performTextInput("new message")
            onNodeWithTag("websocket-send").assertIsEnabled().performClick()

            assertEquals("new message", sent)
            onNodeWithText("new message").assertDoesNotExist()
        }

    @Test
    fun `composer is disabled while disconnected`() =
        runComposeUiTest {
            setContent {
                AppTheme {
                    WebSocketPanel(
                        state = WebSocketTabState(status = WebSocketStatus.DISCONNECTED),
                        onSendMessage = {},
                        onClearMessages = {},
                    )
                }
            }

            onNodeWithText("Connect to start exchanging messages.").assertExists()
            onNodeWithTag("websocket-message-input").performTextInput("blocked")
            onNodeWithTag("websocket-send").assertIsNotEnabled()
        }

    @Test
    fun `clear action is available for a non-empty transcript`() =
        runComposeUiTest {
            var cleared = false
            setContent {
                AppTheme {
                    WebSocketPanel(
                        state =
                            WebSocketTabState(
                                messages = listOf(WebSocketMessage(direction = WebSocketDirection.SYSTEM, content = "Connection closed")),
                            ),
                        onSendMessage = {},
                        onClearMessages = { cleared = true },
                    )
                }
            }

            onNodeWithTag("websocket-clear").performClick()
            assertTrue(cleared)
        }
}
