package su.kidoz.postest.ui.components.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.WebSocketDirection
import su.kidoz.postest.domain.model.WebSocketMessage
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.ui.theme.MonospaceFont
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun WebSocketPanel(
    state: WebSocketTabState,
    onSendMessage: (String) -> Unit,
    onClearMessages: () -> Unit,
    modifier: Modifier = Modifier,
    onAutoReconnectChange: (Boolean) -> Unit = {},
    onSendBinary: () -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(tonalElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WebSocketStatusIndicator(state.status)
                Text(
                    text =
                        when (state.status) {
                            WebSocketStatus.DISCONNECTED -> "Disconnected"
                            WebSocketStatus.CONNECTING -> "Connecting…"
                            WebSocketStatus.CONNECTED -> "Connected"
                        },
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.autoReconnect,
                        onCheckedChange = onAutoReconnectChange,
                        modifier = Modifier.testTag("websocket-auto-reconnect"),
                    )
                    Text("Auto-reconnect", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onClearMessages,
                    enabled = state.messages.isNotEmpty(),
                    modifier = Modifier.testTag("websocket-clear"),
                ) {
                    Text("Clear")
                }
            }
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Text(
                    text =
                        when (state.status) {
                            WebSocketStatus.CONNECTED -> "Connected. Send a message below."
                            WebSocketStatus.CONNECTING -> "Opening connection…"
                            WebSocketStatus.DISCONNECTED -> "Connect to start exchanging messages."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("websocket-messages"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        WebSocketMessageRow(message)
                    }
                }
            }
        }

        HorizontalDivider()
        Surface(tonalElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag("websocket-message-input")
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Enter &&
                                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                                    keyEvent.type == KeyEventType.KeyDown
                                ) {
                                    sendDraft(state.status, draft, onSendMessage) { draft = "" }
                                    true
                                } else {
                                    false
                                }
                            },
                    placeholder = { Text("Message") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFont),
                    minLines = 1,
                    maxLines = 4,
                )
                IconButton(
                    onClick = onSendBinary,
                    enabled = state.status == WebSocketStatus.CONNECTED,
                    modifier = Modifier.testTag("websocket-send-binary"),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Send binary file")
                }
                Button(
                    onClick = { sendDraft(state.status, draft, onSendMessage) { draft = "" } },
                    enabled = state.status == WebSocketStatus.CONNECTED && draft.isNotBlank(),
                    modifier = Modifier.height(56.dp).testTag("websocket-send"),
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Send")
                }
            }
        }
    }
}

private fun sendDraft(
    status: WebSocketStatus,
    draft: String,
    onSendMessage: (String) -> Unit,
    clear: () -> Unit,
) {
    if (status == WebSocketStatus.CONNECTED && draft.isNotBlank()) {
        onSendMessage(draft)
        clear()
    }
}

@Composable
private fun WebSocketStatusIndicator(status: WebSocketStatus) {
    val color =
        when (status) {
            WebSocketStatus.CONNECTED -> MaterialTheme.colorScheme.primary
            WebSocketStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary
            WebSocketStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50)),
    )
}

@Composable
private fun WebSocketMessageRow(message: WebSocketMessage) {
    if (message.direction == WebSocketDirection.SYSTEM) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val sent = message.direction == WebSocketDirection.SENT
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (sent) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f),
            shape = RoundedCornerShape(8.dp),
            color =
                if (sent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (sent) "Sent" else "Received",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = messageTimeFormatter.format(Instant.ofEpochMilli(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonospaceFont),
                    )
                }
                if (message.truncated) {
                    Text(
                        text = "Message truncated to 1 MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
