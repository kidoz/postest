package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpRequest

@Composable
fun RequestPanel(
    request: HttpRequest,
    onRequestChange: (HttpRequest) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // URL Bar
        UrlBar(
            method = request.method,
            url = request.url,
            onMethodChange = { onRequestChange(request.copy(method = it)) },
            onUrlChange = { onRequestChange(request.copy(url = it)) },
            onSend = onSend,
            isLoading = isLoading,
        )

        // Tabs
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("Params", "Headers", "Body", "Auth")

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row {
                            Text(title)
                            // Show count badges
                            val count =
                                when (index) {
                                    0 -> request.queryParams.count { it.enabled && it.key.isNotBlank() }
                                    1 -> request.headers.count { it.enabled && it.key.isNotBlank() }
                                    else -> 0
                                }
                            if (count > 0) {
                                Spacer(Modifier.width(4.dp))
                                Badge { Text(count.toString()) }
                            }
                        }
                    },
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 ->
                    ParamsEditor(
                        params = request.queryParams,
                        onParamsChange = { onRequestChange(request.copy(queryParams = it)) },
                    )

                1 ->
                    HeadersEditor(
                        headers = request.headers,
                        onHeadersChange = { onRequestChange(request.copy(headers = it)) },
                    )

                2 ->
                    BodyEditor(
                        body = request.body,
                        onBodyChange = { onRequestChange(request.copy(body = it)) },
                    )

                3 ->
                    AuthPanel(
                        auth = request.auth,
                        onAuthChange = { onRequestChange(request.copy(auth = it)) },
                    )
            }
        }
    }
}
