package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.ui.theme.AppTheme

data class TabItem(
    val id: String,
    val title: String,
    val method: HttpMethod,
    val isDirty: Boolean = false,
)

@Composable
fun RequestTabBar(
    tabs: List<TabItem>,
    selectedTabId: String?,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            RequestTab(
                tab = tab,
                isSelected = tab.id == selectedTabId,
                onClick = { onTabSelect(tab.id) },
                onClose = { onTabClose(tab.id) },
            )
        }
    }
}

@Composable
private fun RequestTab(
    tab: TabItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val extendedColors = AppTheme.extendedColors
    val methodColor =
        when (tab.method) {
            HttpMethod.GET -> extendedColors.methodGet
            HttpMethod.POST -> extendedColors.methodPost
            HttpMethod.PUT -> extendedColors.methodPut
            HttpMethod.PATCH -> extendedColors.methodPatch
            HttpMethod.DELETE -> extendedColors.methodDelete
            HttpMethod.HEAD -> extendedColors.methodHead
            HttpMethod.OPTIONS -> extendedColors.methodOptions
        }

    Surface(
        modifier =
            Modifier
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = tab.method.name,
                style = MaterialTheme.typography.labelSmall,
                color = methodColor,
            )

            Text(
                text = if (tab.isDirty) "${tab.title} *" else tab.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(16.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
