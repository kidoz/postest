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
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.domain.model.WebSocketStatus
import su.kidoz.postest.domain.model.WebSocketTabState
import su.kidoz.postest.ui.components.common.ConfirmDialog
import su.kidoz.postest.ui.components.common.DesktopIconButton
import su.kidoz.postest.ui.components.common.HorizontalSplitPane
import su.kidoz.postest.ui.components.common.RequestTabBar
import su.kidoz.postest.ui.components.common.TabItem
import su.kidoz.postest.ui.components.common.VerticalSplitPane
import su.kidoz.postest.ui.components.request.RequestPanel
import su.kidoz.postest.ui.components.response.ResponsePanel
import su.kidoz.postest.ui.components.response.WebSocketPanel
import su.kidoz.postest.ui.components.sidebar.Sidebar
import su.kidoz.postest.util.requestDisplayName
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

    // Add request to collection dialog state
    var showAddRequestDialog by remember { mutableStateOf(false) }
    var addRequestTargetCollection by remember { mutableStateOf<RequestCollection?>(null) }
    var newRequestName by remember { mutableStateOf("") }

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

    // File picker for WebSocket binary messages
    val openWebSocketBinaryDialog = {
        val fileChooser =
            JFileChooser().apply {
                dialogTitle = "Send Binary Message"
                isAcceptAllFileFilterUsed = true
            }
        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            viewModel.sendWebSocketBinary(fileChooser.selectedFile)
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
                        selectedCollectionItemId = state.activeTab?.collectionItemId,
                        onRequestClick = { request ->
                            // Use the collection item's name for the request
                            viewModel.openRequest(
                                request = request.request.copy(name = request.name),
                                collectionItemId = request.id,
                            )
                        },
                        onHistoryClick = { entry ->
                            viewModel.openHistoryEntry(entry)
                        },
                        onNewCollection = { showNewCollectionDialog = true },
                        onNewRequest = { collection ->
                            // Show dialog to enter request name
                            addRequestTargetCollection = collection
                            newRequestName = state.activeTab?.request?.name ?: ""
                            showAddRequestDialog = true
                        },
                        onCreateFolder = { collectionId, parentFolderId, name ->
                            viewModel.addFolderToCollection(collectionId, name, parentFolderId)
                        },
                        onImportCollection = openImportDialog,
                        onExportCollection = openExportDialog,
                        onRenameCollection = { collection, newName ->
                            viewModel.renameCollection(collection.id, newName)
                        },
                        onDeleteCollection = { collection ->
                            viewModel.deleteCollection(collection.id)
                        },
                        onRenameItem = { item, newName ->
                            viewModel.renameCollectionItem(item.id, newName)
                        },
                        onDeleteItem = { item ->
                            viewModel.deleteCollectionItem(item.id)
                        },
                        onMoveItem = { item, targetFolderId ->
                            viewModel.moveCollectionItem(item.id, targetFolderId)
                        },
                        onClearHistory = { viewModel.clearHistory() },
                        onEnvironmentSelect = { viewModel.selectEnvironment(it) },
                        onManageEnvironments = { viewModel.showEnvironmentDialog() },
                    )
                },
                rightContent = {
                    RequestArea(
                        state = state,
                        viewModel = viewModel,
                        onSendBinary = openWebSocketBinaryDialog,
                    )
                },
            )

            // Status bar
            StatusBar(state = state)
        }
    }

    val pendingCloseTabs = state.tabs.filter { it.id in state.pendingCloseTabIds }
    if (pendingCloseTabs.isNotEmpty()) {
        val dirtyTabs = pendingCloseTabs.filter { it.isDirty }
        val singleTab = pendingCloseTabs.singleOrNull()
        ConfirmDialog(
            title =
                if (singleTab != null) {
                    "Discard request changes?"
                } else {
                    "Discard changes in ${dirtyTabs.size} ${if (dirtyTabs.size == 1) "tab" else "tabs"}?"
                },
            message =
                if (singleTab != null) {
                    "Changes to “${requestDisplayName(singleTab.request.name, singleTab.request.url)}” will be lost."
                } else if (dirtyTabs.size == pendingCloseTabs.size) {
                    "Changes in ${dirtyTabs.size} tabs will be lost."
                } else {
                    "Changes in ${dirtyTabs.size} of ${pendingCloseTabs.size} tabs will be lost."
                },
            confirmText = "Discard",
            isDestructive = true,
            onConfirm = viewModel::discardPendingTabs,
            onDismiss = viewModel::cancelTabClose,
        )
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

    // Add Request to Collection Dialog
    if (showAddRequestDialog && addRequestTargetCollection != null) {
        AlertDialog(
            onDismissRequest = {
                showAddRequestDialog = false
                addRequestTargetCollection = null
                newRequestName = ""
            },
            title = { Text("Add Request to Collection") },
            text = {
                OutlinedTextField(
                    value = newRequestName,
                    onValueChange = { newRequestName = it },
                    label = { Text("Request Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addRequestTargetCollection?.let { collection ->
                            viewModel.addRequestToCollection(collection, newRequestName)
                        }
                        showAddRequestDialog = false
                        addRequestTargetCollection = null
                        newRequestName = ""
                    },
                    enabled = newRequestName.isNotBlank(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddRequestDialog = false
                        addRequestTargetCollection = null
                        newRequestName = ""
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
            activeEnvironmentId = state.activeEnvironmentId,
            onDismiss = { viewModel.hideEnvironmentDialog() },
            onSave = { viewModel.saveEnvironment(it) },
            onDelete = { viewModel.deleteEnvironment(it) },
            onSetActive = { viewModel.selectEnvironment(it) },
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
        color = MaterialTheme.colorScheme.surfaceVariant,
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
                                title = requestDisplayName(tab.request.name, tab.request.url),
                                method = tab.request.method,
                                isDirty = tab.isDirty,
                                protocol = tab.request.protocol,
                            )
                        },
                    selectedTabId = state.activeTabId,
                    onTabSelect = { viewModel.selectTab(it) },
                    onTabClose = { viewModel.closeTab(it) },
                    onCloseAll = viewModel::closeAllTabs,
                    onCloseOthers = viewModel::closeOtherTabs,
                    onCloseToLeft = viewModel::closeTabsToLeft,
                    onCloseToRight = viewModel::closeTabsToRight,
                )
            }

            // New tab button
            DesktopIconButton(Icons.Default.Add, "New tab (⌘/Ctrl+T)", { viewModel.newTab() })

            // Theme toggle
            DesktopIconButton(
                icon = if (state.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                description = if (state.isDarkTheme) "Switch to light theme" else "Switch to dark theme",
                onClick = { viewModel.toggleTheme() },
            )
        }
    }
}

@Composable
private fun RequestArea(
    state: AppState,
    viewModel: MainViewModel,
    onSendBinary: () -> Unit,
) {
    val activeTab = state.activeTab

    if (activeTab != null) {
        val webSocket = activeTab.webSocket
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
                    webSocketStatus = webSocket?.status ?: WebSocketStatus.DISCONNECTED,
                    onConnect = { viewModel.connectWebSocket() },
                    onDisconnect = { viewModel.disconnectWebSocket() },
                    onAuthorizeOAuth2 = { viewModel.authorizeOAuth2() },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            bottomContent = {
                if (activeTab.request.protocol == RequestProtocol.WEBSOCKET) {
                    WebSocketPanel(
                        state = webSocket ?: WebSocketTabState(),
                        onSendMessage = { viewModel.sendWebSocketMessage(it) },
                        onClearMessages = { viewModel.clearWebSocketMessages() },
                        onAutoReconnectChange = { viewModel.setWebSocketAutoReconnect(it) },
                        onSendBinary = onSendBinary,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ResponsePanel(
                        response = activeTab.response,
                        error = activeTab.error,
                        isLoading = activeTab.isLoading,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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

        else -> {
            false
        }
    }
}
