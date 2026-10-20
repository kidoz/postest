package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.ui.components.common.SectionTabs

@Composable
fun RequestPanel(
    request: HttpRequest,
    onRequestChange: (HttpRequest) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    webSocketStatus: WebSocketStatus = WebSocketStatus.DISCONNECTED,
    onConnect: () -> Unit = onSend,
    onDisconnect: () -> Unit = {},
) {
    Column(modifier = modifier) {
        // URL Bar
        UrlBar(
            method = request.method,
            protocol = request.protocol,
            url = request.url,
            onMethodChange = { onRequestChange(request.copy(method = it)) },
            onProtocolChange = { onRequestChange(request.copy(protocol = it)) },
            onUrlChange = { newUrl -> onRequestChange(request.copy(url = newUrl)) },
            onSend = onSend,
            isLoading = isLoading,
            webSocketStatus = webSocketStatus,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )

        // Tabs
        var selectedTab by remember(request.protocol) { mutableStateOf(0) }
        val tabs =
            if (request.protocol == RequestProtocol.WEBSOCKET) {
                listOf("Params", "Headers", "Auth")
            } else {
                listOf("Params", "Headers", "Body", "Auth")
            }

        SectionTabs(
            titles =
                tabs.map { title ->
                    val count =
                        when (title) {
                            "Params" -> request.queryParams.count { it.enabled && it.key.isNotBlank() }
                            "Headers" -> request.headers.count { it.enabled && it.key.isNotBlank() }
                            else -> 0
                        }
                    if (count > 0) "$title ($count)" else title
                },
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (tabs.getOrNull(selectedTab)) {
                "Params" -> {
                    ParamsEditor(
                        params = request.queryParams,
                        onParamsChange = { onRequestChange(request.copy(queryParams = it)) },
                    )
                }

                "Headers" -> {
                    HeadersEditor(
                        headers = request.headers,
                        onHeadersChange = { onRequestChange(request.copy(headers = it)) },
                    )
                }

                "Body" -> {
                    BodyEditor(
                        body = request.body,
                        onBodyChange = { onRequestChange(request.copy(body = it)) },
                    )
                }

                "Auth" -> {
                    AuthPanel(
                        auth = request.auth,
                        clientCertificate = request.clientCertificate,
                        onAuthChange = { onRequestChange(request.copy(auth = it)) },
                        onClientCertChange = { onRequestChange(request.copy(clientCertificate = it)) },
                        showClientCertificate = request.protocol == RequestProtocol.HTTP,
                    )
                }
            }
        }
    }
}
