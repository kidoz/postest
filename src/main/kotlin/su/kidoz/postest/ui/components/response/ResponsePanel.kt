package su.kidoz.postest.ui.components.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.isClientError
import su.kidoz.postest.domain.model.isRedirect
import su.kidoz.postest.domain.model.isServerError
import su.kidoz.postest.domain.model.isSuccess
import su.kidoz.postest.ui.theme.AppTheme

@Composable
fun ResponsePanel(
    response: HttpResponse?,
    error: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Response header
        ResponseStatusBar(response = response, error = error, isLoading = isLoading)

        HorizontalDivider()

        // Response content
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Sending request...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            response != null -> {
                ResponseContent(response = response)
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Send a request to see the response",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseStatusBar(
    response: HttpResponse?,
    error: String?,
    isLoading: Boolean,
) {
    val extendedColors = AppTheme.extendedColors
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Response",
            style = MaterialTheme.typography.titleMedium,
        )

        if (response != null && !isLoading) {
            // Status badge
            val statusColor =
                when {
                    response.isSuccess() -> extendedColors.status2xx
                    response.isRedirect() -> extendedColors.status3xx
                    response.isClientError() -> extendedColors.status4xx
                    response.isServerError() -> extendedColors.status5xx
                    else -> MaterialTheme.colorScheme.onSurface
                }

            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "${response.statusCode} ${response.statusText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
            }

            // Time
            Text(
                text = "${response.time.total}ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Size
            Text(
                text = formatSize(response.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            // Copy button
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(response.body))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy response",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else if (error != null) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ResponseContent(response: HttpResponse) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Body", "Headers")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 ->
                    ResponseBody(
                        body = response.body,
                        contentType = response.contentType,
                    )

                1 ->
                    ResponseHeaders(
                        headers = response.headers,
                    )
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
