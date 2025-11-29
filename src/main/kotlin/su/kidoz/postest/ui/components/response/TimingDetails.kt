package su.kidoz.postest.ui.components.response

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.ResponseTime

/**
 * Displays detailed timing breakdown for HTTP response.
 * Shows a waterfall-style visualization of request phases.
 */
@Composable
fun TimingDetails(
    responseTime: ResponseTime,
    modifier: Modifier = Modifier,
) {
    val total = responseTime.total.toFloat().coerceAtLeast(1f)

    Surface(
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Timing Breakdown",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Time to First Byte (TTFB)
            responseTime.firstByte?.let { ttfb ->
                TimingRow(
                    label = "Time to First Byte",
                    value = ttfb,
                    total = total,
                    color = TimingColors.firstByte,
                )
            }

            // Download time
            responseTime.download?.let { download ->
                TimingRow(
                    label = "Content Download",
                    value = download,
                    total = total,
                    color = TimingColors.download,
                    offset = responseTime.firstByte ?: 0,
                )
            }

            // Total
            TimingRow(
                label = "Total",
                value = responseTime.total,
                total = total,
                color = TimingColors.total,
                showBar = false,
            )
        }
    }
}

@Composable
private fun TimingRow(
    label: String,
    value: Long,
    total: Float,
    color: Color,
    offset: Long = 0,
    showBar: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBar) {
                    Box(
                        modifier =
                            Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatDuration(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (showBar) {
            // Waterfall bar
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val offsetFraction = (offset.toFloat() / total).coerceIn(0f, 1f)
                val widthFraction = (value.toFloat() / total).coerceIn(0f, 1f - offsetFraction)

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(offsetFraction + widthFraction)
                            .height(6.dp)
                            .padding(start = (offsetFraction * 260).dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String =
    when {
        ms < 1 -> "<1 ms"
        ms < 1000 -> "$ms ms"
        else -> String.format(java.util.Locale.US, "%.2f s", ms / 1000.0)
    }

private object TimingColors {
    val firstByte = Color(0xFF4CAF50) // Green - connection + waiting
    val download = Color(0xFF2196F3) // Blue - content download
    val total = Color(0xFF9E9E9E) // Gray - total
}
