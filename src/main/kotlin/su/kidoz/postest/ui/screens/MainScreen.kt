package su.kidoz.postest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.ui.components.common.HorizontalSplitPane
import su.kidoz.postest.ui.components.common.RequestTabBar
import su.kidoz.postest.ui.components.common.TabItem
import su.kidoz.postest.ui.components.common.VerticalSplitPane
import su.kidoz.postest.ui.components.request.RequestPanel
import su.kidoz.postest.ui.components.response.ResponsePanel
import su.kidoz.postest.ui.components.sidebar.Sidebar
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.MainViewModel
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    // File picker for import
    val openImportDialog = {
        val fileChooser =
            JFileChooser().apply {
                dialogTitle = "Import Collection"
                fileFilter =
                    FileNameExtensionFilter(
                        "Collections (Postman/OpenAPI JSON/YAML)",
                        "json",
                        "yaml",
                        "yml",
                    )
                isAcceptAllFileFilterUsed = false
            }
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            viewModel.importCollection(fileChooser.selectedFile)
        }
    }

    // File picker for export
    val openExportDialog: (RequestCollection) -> Unit = { collection ->
        val suggestedName = "${collection.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.postman_collection.json"
        val fileChooser =
            JFileChooser().apply {
                dialogTitle = "Export Collection to Postman"
                fileFilter = FileNameExtensionFilter("Postman Collection (*.json)", "json")
                isAcceptAllFileFilterUsed = false
                selectedFile = File(suggestedName)
            }
        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var targetFile = fileChooser.selectedFile
            // Ensure .json extension
            if (!targetFile.name.endsWith(".json")) {
                targetFile = File(targetFile.parentFile, "${targetFile.name}.json")
            }
            viewModel.exportCollection(collection.id, targetFile)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier =
            modifier
                .fillMaxSize()
                .onKeyEvent { keyEvent ->
                    handleKeyboardShortcuts(keyEvent, viewModel, state)
                },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            // Top bar
            TopAppBar(state = state, viewModel = viewModel)

            HorizontalDivider()

            // Main content
            HorizontalSplitPane(
                modifier = Modifier.weight(1f),
                splitFraction = 0.2f,
                minLeftFraction = 0.15f,
                maxLeftFraction = 0.4f,
                leftContent = {
                    Sidebar(
                        collections = state.collections,
                        history = state.history,
                        environments = state.environments,
                        selectedEnvironmentId = state.activeEnvironmentId,
                        onRequestClick = { request ->
                            viewModel.openRequest(request.request)
                        },
                        onHistoryClick = { entry ->
                            viewModel.openHistoryEntry(entry)
                        },
                        onNewCollection = { showNewCollectionDialog = true },
                        onNewRequest = { collection ->
                            viewModel.addRequestToCollection(collection)
                        },
                        onImportCollection = openImportDialog,
                        onExportCollection = openExportDialog,
                        onDeleteCollection = { collection ->
                            viewModel.deleteCollection(collection.id)
                        },
                        onDeleteItem = { item ->
                            viewModel.deleteCollectionItem(item.id)
                        },
                        onClearHistory = { viewModel.clearHistory() },
                        onEnvironmentSelect = { viewModel.selectEnvironment(it) },
                        onManageEnvironments = { viewModel.showEnvironmentDialog() },
                    )
                },
                rightContent = {
                    RequestArea(state = state, viewModel = viewModel)
                },
            )

            // Status bar
            StatusBar(state = state)
        }
    }

    // New Collection Dialog
    if (showNewCollectionDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewCollectionDialog = false
                newCollectionName = ""
            },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCollectionName.isNotBlank()) {
                            viewModel.createCollection(newCollectionName)
                            showNewCollectionDialog = false
                            newCollectionName = ""
                        }
                    },
                    enabled = newCollectionName.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewCollectionDialog = false
                        newCollectionName = ""
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Environment Manager Dialog
    if (state.showEnvironmentDialog) {
        EnvironmentManagerDialog(
            environments = state.environments,
            onDismiss = { viewModel.hideEnvironmentDialog() },
            onSave = { viewModel.saveEnvironment(it) },
            onDelete = { viewModel.deleteEnvironment(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    state: AppState,
    viewModel: MainViewModel,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App title
            Text(
                text = "Postest",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            // Tab bar
            Box(modifier = Modifier.weight(1f)) {
                RequestTabBar(
                    tabs =
                        state.tabs.map { tab ->
                            TabItem(
                                id = tab.id,
                                title = tab.request.name.ifBlank { tab.request.url.ifBlank { "Untitled" } },
                                method = tab.request.method,
                                isDirty = tab.isDirty,
                            )
                        },
                    selectedTabId = state.activeTabId,
                    onTabSelect = { viewModel.selectTab(it) },
                    onTabClose = { viewModel.closeTab(it) },
                )
            }

            // New tab button
            IconButton(onClick = { viewModel.newTab() }) {
                Icon(Icons.Default.Add, contentDescription = "New tab")
            }

            // Theme toggle
            IconButton(onClick = { viewModel.toggleTheme() }) {
                Icon(
                    if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme",
                )
            }
        }
    }
}

@Composable
private fun RequestArea(
    state: AppState,
    viewModel: MainViewModel,
) {
    val activeTab = state.activeTab

    if (activeTab != null) {
        VerticalSplitPane(
            modifier = Modifier.fillMaxSize(),
            splitFraction = 0.5f,
            minTopFraction = 0.2f,
            maxTopFraction = 0.8f,
            topContent = {
                RequestPanel(
                    request = activeTab.request,
                    onRequestChange = { viewModel.updateRequest(it) },
                    onSend = { viewModel.sendRequest() },
                    isLoading = activeTab.isLoading,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            bottomContent = {
                ResponsePanel(
                    response = activeTab.response,
                    error = activeTab.error,
                    isLoading = activeTab.isLoading,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No request selected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Environment: ${state.activeEnvironment?.name ?: "None"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "${state.collections.size} collections | ${state.history.size} history items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun handleKeyboardShortcuts(
    keyEvent: KeyEvent,
    viewModel: MainViewModel,
    state: AppState,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed

    return when {
        // Ctrl/Cmd + Enter: Send request
        isCtrlOrCmd && keyEvent.key == Key.Enter -> {
            viewModel.sendRequest()
            true
        }

        // Ctrl/Cmd + T: New tab
        isCtrlOrCmd && keyEvent.key == Key.T -> {
            viewModel.newTab()
            true
        }

        // Ctrl/Cmd + W: Close tab
        isCtrlOrCmd && keyEvent.key == Key.W -> {
            state.activeTabId?.let { viewModel.closeTab(it) }
            true
        }

        // Ctrl/Cmd + N: New request
        isCtrlOrCmd && keyEvent.key == Key.N -> {
            viewModel.newTab()
            true
        }

        else -> false
    }
}
