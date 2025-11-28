package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.RequestCollection

enum class SidebarTab(
    val title: String,
) {
    COLLECTIONS("Collections"),
    HISTORY("History"),
}

@Composable
fun Sidebar(
    collections: List<RequestCollection>,
    history: List<HistoryEntry>,
    environments: List<Environment>,
    selectedEnvironmentId: String?,
    onRequestClick: (CollectionItem.Request) -> Unit,
    onHistoryClick: (HistoryEntry) -> Unit,
    onNewCollection: () -> Unit,
    onNewRequest: (RequestCollection) -> Unit,
    onImportCollection: () -> Unit,
    onExportCollection: (RequestCollection) -> Unit,
    onRenameCollection: (RequestCollection, String) -> Unit,
    onDeleteCollection: (RequestCollection) -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    onClearHistory: () -> Unit,
    onEnvironmentSelect: (String?) -> Unit,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(SidebarTab.COLLECTIONS) }

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        // Environment selector
        EnvironmentSelector(
            environments = environments,
            selectedEnvironmentId = selectedEnvironmentId,
            onEnvironmentSelect = onEnvironmentSelect,
            onManageEnvironments = onManageEnvironments,
        )

        HorizontalDivider()

        // Tab selector
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SidebarTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        // Tab content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SidebarTab.COLLECTIONS -> {
                    CollectionTree(
                        collections = collections,
                        onRequestClick = onRequestClick,
                        onNewCollection = onNewCollection,
                        onNewRequest = onNewRequest,
                        onImportCollection = onImportCollection,
                        onExportCollection = onExportCollection,
                        onRenameCollection = onRenameCollection,
                        onDeleteCollection = onDeleteCollection,
                        onRenameItem = onRenameItem,
                        onDeleteItem = onDeleteItem,
                    )
                }

                SidebarTab.HISTORY -> {
                    HistoryList(
                        history = history,
                        onHistoryClick = onHistoryClick,
                        onClearHistory = onClearHistory,
                    )
                }
            }
        }
    }
}
