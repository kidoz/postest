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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.ui.theme.AppTheme

data class TabItem(
    val id: String,
    val title: String,
    val method: HttpMethod,
    val isDirty: Boolean = false,
    val protocol: RequestProtocol = RequestProtocol.HTTP,
)

@Composable
fun RequestTabBar(
    tabs: List<TabItem>,
    selectedTabId: String?,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onCloseAll: () -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseToLeft: (String) -> Unit,
    onCloseToRight: (String) -> Unit,
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
        tabs.forEachIndexed { index, tab ->
            RequestTab(
                tab = tab,
                index = index,
                tabCount = tabs.size,
                isSelected = tab.id == selectedTabId,
                onClick = { onTabSelect(tab.id) },
                onClose = { onTabClose(tab.id) },
                onCloseAll = onCloseAll,
                onCloseOthers = { onCloseOthers(tab.id) },
                onCloseToLeft = { onCloseToLeft(tab.id) },
                onCloseToRight = { onCloseToRight(tab.id) },
            )
        }
    }
}

@Composable
private fun RequestTab(
    tab: TabItem,
    index: Int,
    tabCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onCloseAll: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseToLeft: () -> Unit,
    onCloseToRight: () -> Unit,
) {
    val extendedColors = AppTheme.extendedColors
    val methodColor =
        if (tab.protocol == RequestProtocol.WEBSOCKET) {
            extendedColors.methodWebSocket
        } else {
            when (tab.method) {
                HttpMethod.GET -> extendedColors.methodGet
                HttpMethod.POST -> extendedColors.methodPost
                HttpMethod.PUT -> extendedColors.methodPut
                HttpMethod.PATCH -> extendedColors.methodPatch
                HttpMethod.DELETE -> extendedColors.methodDelete
                HttpMethod.HEAD -> extendedColors.methodHead
                HttpMethod.OPTIONS -> extendedColors.methodOptions
            }
        }
    val methodLabel = if (tab.protocol == RequestProtocol.WEBSOCKET) "WS" else tab.method.name

    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .testTag("tab-${tab.id}")
                .pointerInput(tab.id) {
                    awaitPointerEventScope {
                        var secondaryPressed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            when {
                                event.type == PointerEventType.Press && event.buttons.isSecondaryPressed -> {
                                    secondaryPressed = true
                                }

                                event.type == PointerEventType.Release && secondaryPressed -> {
                                    secondaryPressed = false
                                    showContextMenu = true
                                }
                            }
                        }
                    }
                },
    ) {
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
                        text = methodLabel,
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

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Close All") },
                onClick = {
                    showContextMenu = false
                    onCloseAll()
                },
            )
            DropdownMenuItem(
                text = { Text("Close Others") },
                enabled = tabCount > 1,
                onClick = {
                    showContextMenu = false
                    onCloseOthers()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Close Tabs to the Left") },
                enabled = index > 0,
                onClick = {
                    showContextMenu = false
                    onCloseToLeft()
                },
            )
            DropdownMenuItem(
                text = { Text("Close Tabs to the Right") },
                enabled = index < tabCount - 1,
                onClick = {
                    showContextMenu = false
                    onCloseToRight()
                },
            )
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
