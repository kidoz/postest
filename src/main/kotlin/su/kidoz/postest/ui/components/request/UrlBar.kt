package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.ui.components.common.CompactTextField
import su.kidoz.postest.ui.theme.MonospaceFont

@Composable
fun UrlBar(
    method: HttpMethod,
    url: String,
    onMethodChange: (HttpMethod) -> Unit,
    onUrlChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    protocol: RequestProtocol = RequestProtocol.HTTP,
    onProtocolChange: (RequestProtocol) -> Unit = {},
    webSocketStatus: WebSocketStatus = WebSocketStatus.DISCONNECTED,
    onConnect: () -> Unit = onSend,
    onDisconnect: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MethodSelector(
            selected = method,
            onSelect = onMethodChange,
            protocol = protocol,
            onProtocolSelect = onProtocolChange,
        )

        CompactTextField(
            value = url,
            onValueChange = onUrlChange,
            label = if (protocol == RequestProtocol.WEBSOCKET) "Enter WebSocket URL (ws:// or wss://)" else "Enter request URL",
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFont),
            modifier =
                Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter &&
                            (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                            keyEvent.type == KeyEventType.KeyDown
                        ) {
                            if (protocol == RequestProtocol.WEBSOCKET) {
                                if (webSocketStatus == WebSocketStatus.CONNECTED) onDisconnect() else onConnect()
                            } else {
                                onSend()
                            }
                            true
                        } else {
                            false
                        }
                    },
        )

        if (protocol == RequestProtocol.WEBSOCKET) {
            WebSocketActionButton(
                status = webSocketStatus,
                enabled = url.isNotBlank(),
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )
        } else {
            Button(
                onClick = onSend,
                modifier = Modifier.height(40.dp).widthIn(min = 96.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                enabled = !isLoading && url.isNotBlank(),
                shape = RoundedCornerShape(4.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("Send")
            }
        }
    }
}

@Composable
private fun WebSocketActionButton(
    status: WebSocketStatus,
    enabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = status == WebSocketStatus.CONNECTED
    val connecting = status == WebSocketStatus.CONNECTING
    Button(
        onClick = if (connected) onDisconnect else onConnect,
        modifier = Modifier.height(40.dp).widthIn(min = 112.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        enabled = enabled && !connecting,
        shape = RoundedCornerShape(4.dp),
        colors =
            if (connected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Connecting")
        } else {
            Text(if (connected) "Disconnect" else "Connect")
        }
    }
}
