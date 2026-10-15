package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
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

    AppTooltip(tab.title) {
        Surface(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
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

                DesktopIconButton(
                    icon = Icons.Default.Close,
                    description = "Close tab",
                    onClick = onClose,
                )
            }
        }
    }
}

@Composable
fun SectionTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.background(colors.surface)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup()) {
            titles.forEachIndexed { index, title ->
                val selected = selectedIndex == index
                Box(
                    modifier =
                        Modifier
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                            .height(height)
                            .drawBehind {
                                if (selected) {
                                    val stroke = 2.dp.toPx()
                                    drawLine(
                                        colors.primary,
                                        Offset(0f, size.height - stroke / 2),
                                        Offset(
                                            size.width,
                                            size.height - stroke / 2,
                                        ),
                                        stroke,
                                    )
                                }
                            }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
