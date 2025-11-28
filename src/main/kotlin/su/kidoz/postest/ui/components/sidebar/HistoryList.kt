package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryList(
    history: List<HistoryEntry>,
    onHistoryClick: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleSmall,
            )

            if (history.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear history",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        HorizontalDivider()

        // History list
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No history yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // Group by date
            val groupedHistory =
                history.groupBy { entry ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.timestamp))
                }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                groupedHistory.forEach { (date, entries) ->
                    item {
                        Text(
                            text = formatDate(date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    items(entries) { entry ->
                        HistoryItem(
                            entry = entry,
                            onClick = { onHistoryClick(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    onClick: () -> Unit,
) {
    val extendedColors = AppTheme.extendedColors
    val methodColor =
        when (entry.request.method) {
            HttpMethod.GET -> extendedColors.methodGet
            HttpMethod.POST -> extendedColors.methodPost
            HttpMethod.PUT -> extendedColors.methodPut
            HttpMethod.PATCH -> extendedColors.methodPatch
            HttpMethod.DELETE -> extendedColors.methodDelete
            HttpMethod.HEAD -> extendedColors.methodHead
            HttpMethod.OPTIONS -> extendedColors.methodOptions
        }

    val statusColor =
        entry.response?.let { response ->
            when (response.statusCode) {
                in 200..299 -> extendedColors.status2xx
                in 300..399 -> extendedColors.status3xx
                in 400..499 -> extendedColors.status4xx
                in 500..599 -> extendedColors.status5xx
                else -> MaterialTheme.colorScheme.onSurface
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.request.method.name,
            style = MaterialTheme.typography.labelSmall,
            color = methodColor,
            modifier = Modifier.width(48.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    entry.request.url.takeIf { it.isNotBlank() }
                        ?: "No URL",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.response?.let { response ->
                    Text(
                        text = "${response.statusCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor ?: MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = "${entry.duration}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = formatTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDate(dateString: String): String {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
            Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000),
        )

    return when (dateString) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> dateString
    }
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
