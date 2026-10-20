package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.ui.components.common.AppTooltip
import su.kidoz.postest.ui.components.common.ConfirmDialog
import su.kidoz.postest.ui.components.common.DesktopIconButton
import su.kidoz.postest.ui.components.common.TextInputDialog
import su.kidoz.postest.ui.theme.AppTheme
import su.kidoz.postest.util.requestDisplayName

private data class ItemDragCallbacks(
    val onStart: (CollectionItem, Offset) -> Unit,
    val onMove: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
private fun AutoScrollDuringDrag(
    listState: LazyListState,
    position: Offset?,
    viewport: Rect?,
) {
    val edgeSize = with(LocalDensity.current) { 40.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 600.dp.toPx() }
    val speed =
        if (position == null || viewport == null || !viewport.contains(position)) {
            0f
        } else {
            val edge = minOf(edgeSize, viewport.height / 2f)
            when {
                position.y < viewport.top + edge -> -maxSpeed * (viewport.top + edge - position.y) / edge
                position.y > viewport.bottom - edge -> maxSpeed * (position.y - viewport.bottom + edge) / edge
                else -> 0f
            }
        }

    LaunchedEffect(listState, speed) {
        if (speed == 0f) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (isActive) {
            val frame = withFrameNanos { it }
            val distance = speed * ((frame - previousFrame) / 1_000_000_000f)
            previousFrame = frame
            if (listState.scrollBy(distance) == 0f) break
        }
    }
}

@Composable
private fun rememberItemDragModifier(
    item: CollectionItem,
    enabled: Boolean,
    callbacks: ItemDragCallbacks,
): Modifier {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    return remember(item, enabled) {
        if (!enabled) {
            Modifier
        } else {
            Modifier
                .onGloballyPositioned { coordinates.value = it }
                .pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            coordinates.value?.let { currentCallbacks.onStart(item, it.localToRoot(offset)) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            coordinates.value?.let { currentCallbacks.onMove(it.localToRoot(change.position)) }
                        },
                        onDragEnd = { currentCallbacks.onEnd() },
                        onDragCancel = { currentCallbacks.onCancel() },
                    )
                }
        }
    }
}

@Composable
fun CollectionTree(
    collections: List<RequestCollection>,
    onRequestClick: (CollectionItem.Request) -> Unit,
    selectedCollectionItemId: String?,
    onNewCollection: () -> Unit,
    onNewRequest: (RequestCollection) -> Unit,
    onCreateFolder: (collectionId: String, parentFolderId: String?, name: String) -> Unit,
    onImportCollection: () -> Unit,
    onExportCollection: (RequestCollection) -> Unit,
    onRenameCollection: (RequestCollection, String) -> Unit,
    onDeleteCollection: (RequestCollection) -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    onRemoveFromFolder: (CollectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }
    AutoScrollDuringDrag(listState, dragPosition, viewportBounds)

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onNewCollection,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New collection", style = MaterialTheme.typography.labelMedium)
            }

            Box {
                DesktopIconButton(
                    icon = Icons.Default.MoreVert,
                    description = "Collection actions",
                    onClick = { showMenu = true },
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("New Collection") },
                        onClick = {
                            showMenu = false
                            onNewCollection()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import Collection") },
                        onClick = {
                            showMenu = false
                            onImportCollection()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                        },
                    )
                }
            }
        }

        HorizontalDivider()

        // Collections list
        if (collections.isEmpty()) {
            EmptyCollectionsPlaceholder(onNewCollection = onNewCollection)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().onGloballyPositioned { viewportBounds = it.boundsInRoot() },
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(collections, key = { it.id }) { collection ->
                    CollectionNode(
                        collection = collection,
                        onRequestClick = onRequestClick,
                        selectedCollectionItemId = selectedCollectionItemId,
                        onNewRequest = { onNewRequest(collection) },
                        onCreateFolder = { parentFolderId, name ->
                            onCreateFolder(collection.id, parentFolderId, name)
                        },
                        onExportCollection = { onExportCollection(collection) },
                        onRenameCollection = { newName -> onRenameCollection(collection, newName) },
                        onDeleteCollection = { onDeleteCollection(collection) },
                        onRenameItem = onRenameItem,
                        onDeleteItem = onDeleteItem,
                        onRemoveFromFolder = onRemoveFromFolder,
                        onDragPositionChange = { dragPosition = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCollectionsPlaceholder(onNewCollection: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No collections yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onNewCollection) {
                Text("Create Collection")
            }
        }
    }
}

@Composable
private fun CollectionNode(
    collection: RequestCollection,
    onRequestClick: (CollectionItem.Request) -> Unit,
    selectedCollectionItemId: String?,
    onNewRequest: () -> Unit,
    onCreateFolder: (parentFolderId: String?, name: String) -> Unit,
    onExportCollection: () -> Unit,
    onRenameCollection: (String) -> Unit,
    onDeleteCollection: () -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    onRemoveFromFolder: (CollectionItem) -> Unit,
    onDragPositionChange: (Offset?) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var draggedItem by remember { mutableStateOf<CollectionItem?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var headerBounds by remember { mutableStateOf<Rect?>(null) }

    val isDropTarget =
        draggedItem != null &&
            dragPosition?.let { headerBounds?.contains(it) } == true
    val dragCallbacks =
        ItemDragCallbacks(
            onStart = { item, position ->
                draggedItem = item
                dragPosition = position
                onDragPositionChange(position)
            },
            onMove = {
                dragPosition = it
                onDragPositionChange(it)
            },
            onEnd = {
                val item = draggedItem
                val position = dragPosition
                val bounds = headerBounds
                draggedItem = null
                dragPosition = null
                onDragPositionChange(null)
                if (item != null && position != null && bounds != null && bounds.contains(position)) {
                    onRemoveFromFolder(item)
                }
            },
            onCancel = {
                draggedItem = null
                dragPosition = null
                onDragPositionChange(null)
            },
        )

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerBounds = it.boundsInRoot() }
                    .background(
                        if (isDropTarget) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ).clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = collection.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )

            Box {
                DesktopIconButton(
                    icon = Icons.Default.MoreVert,
                    description = "Collection menu",
                    onClick = { showMenu = true },
                )

                CollectionContextMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onAddRequest = {
                        showMenu = false
                        onNewRequest()
                    },
                    onAddFolder = {
                        showMenu = false
                        showNewFolderDialog = true
                    },
                    onExport = {
                        showMenu = false
                        onExportCollection()
                    },
                    onRename = {
                        showMenu = false
                        showRenameDialog = true
                    },
                    onDelete = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                )
            }
        }

        if (expanded) {
            collection.items.forEach { item ->
                CollectionItemNode(
                    item = item,
                    depth = 1,
                    onRequestClick = onRequestClick,
                    selectedCollectionItemId = selectedCollectionItemId,
                    onCreateFolder = onCreateFolder,
                    onRenameItem = onRenameItem,
                    onDeleteItem = onDeleteItem,
                    onRemoveFromFolder = onRemoveFromFolder,
                    dragCallbacks = dragCallbacks,
                )
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New Folder",
            label = "Folder Name",
            confirmText = "Create",
            onConfirm = { name ->
                onCreateFolder(null, name)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename Collection",
            label = "Collection Name",
            initialValue = collection.name,
            confirmText = "Rename",
            onConfirm = { newName ->
                onRenameCollection(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete Collection",
            message = "Are you sure you want to delete '${collection.name}'? This action cannot be undone.",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteCollection()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun CollectionContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddRequest: () -> Unit,
    onAddFolder: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Add Request") },
            onClick = onAddRequest,
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Add Folder") },
            onClick = onAddFolder,
            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Export to Postman") },
            onClick = onExport,
            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete Collection", color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}

@Composable
private fun CollectionItemNode(
    item: CollectionItem,
    depth: Int,
    onRequestClick: (CollectionItem.Request) -> Unit,
    selectedCollectionItemId: String?,
    onCreateFolder: (parentFolderId: String?, name: String) -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    onRemoveFromFolder: (CollectionItem) -> Unit,
    dragCallbacks: ItemDragCallbacks,
) {
    val dragModifier = rememberItemDragModifier(item, depth > 1, dragCallbacks)

    when (item) {
        is CollectionItem.Request -> {
            RequestItemNode(
                item = item,
                depth = depth,
                onRequestClick = onRequestClick,
                selectedCollectionItemId = selectedCollectionItemId,
                onRename = { newName -> onRenameItem(item, newName) },
                onDelete = { onDeleteItem(item) },
                onRemoveFromFolder = { onRemoveFromFolder(item) },
                dragModifier = dragModifier,
            )
        }

        is CollectionItem.Folder -> {
            FolderItemNode(
                item = item,
                depth = depth,
                onRequestClick = onRequestClick,
                selectedCollectionItemId = selectedCollectionItemId,
                onCreateFolder = onCreateFolder,
                onRenameItem = onRenameItem,
                onDeleteItem = onDeleteItem,
                onRemoveFromFolder = onRemoveFromFolder,
                dragCallbacks = dragCallbacks,
                dragModifier = dragModifier,
            )
        }
    }
}

@Composable
private fun RequestItemNode(
    item: CollectionItem.Request,
    depth: Int,
    onRequestClick: (CollectionItem.Request) -> Unit,
    selectedCollectionItemId: String?,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveFromFolder: () -> Unit,
    dragModifier: Modifier,
) {
    val extendedColors = AppTheme.extendedColors
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val methodColor =
        when (item.request.method) {
            HttpMethod.GET -> extendedColors.methodGet
            HttpMethod.POST -> extendedColors.methodPost
            HttpMethod.PUT -> extendedColors.methodPut
            HttpMethod.PATCH -> extendedColors.methodPatch
            HttpMethod.DELETE -> extendedColors.methodDelete
            HttpMethod.HEAD -> extendedColors.methodHead
            HttpMethod.OPTIONS -> extendedColors.methodOptions
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    if (selectedCollectionItemId == item.id) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ).semantics { selected = selectedCollectionItemId == item.id }
                .testTag("collection-item-${item.id}")
                .clickable(onClickLabel = "Open request") { onRequestClick(item) }
                .then(dragModifier)
                .padding(start = (depth * 16 + 8).dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.request.method.name,
            style = MaterialTheme.typography.labelSmall,
            color = methodColor,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
        )

        Spacer(Modifier.width(8.dp))

        val label = requestDisplayName(item.name, item.request.url)
        Box(Modifier.weight(1f)) {
            AppTooltip(label) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box {
            DesktopIconButton(
                icon = Icons.Default.MoreVert,
                description = "Request menu",
                onClick = { showMenu = true },
            )

            ItemContextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                onRename = {
                    showMenu = false
                    showRenameDialog = true
                },
                onDelete = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                onRemoveFromFolder =
                    if (depth > 1) {
                        {
                            showMenu = false
                            onRemoveFromFolder()
                        }
                    } else {
                        null
                    },
            )
        }
    }

    // Dialogs
    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename Request",
            label = "Request Name",
            initialValue = item.name,
            confirmText = "Rename",
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete Request",
            message = "Are you sure you want to delete '${item.name}'?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun FolderItemNode(
    item: CollectionItem.Folder,
    depth: Int,
    onRequestClick: (CollectionItem.Request) -> Unit,
    selectedCollectionItemId: String?,
    onCreateFolder: (parentFolderId: String?, name: String) -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    onRemoveFromFolder: (CollectionItem) -> Unit,
    dragCallbacks: ItemDragCallbacks,
    dragModifier: Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .then(dragModifier)
                    .padding(start = (depth * 16).dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )

            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )

            Spacer(Modifier.width(4.dp))

            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )

            Box {
                DesktopIconButton(
                    icon = Icons.Default.MoreVert,
                    description = "Folder menu",
                    onClick = { showMenu = true },
                )

                ItemContextMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onRename = {
                        showMenu = false
                        showRenameDialog = true
                    },
                    onDelete = {
                        showMenu = false
                        showDeleteConfirm = true
                    },
                    onAddFolder = {
                        showMenu = false
                        showNewFolderDialog = true
                    },
                    onRemoveFromFolder =
                        if (depth > 1) {
                            {
                                showMenu = false
                                onRemoveFromFolder(item)
                            }
                        } else {
                            null
                        },
                )
            }
        }

        if (expanded) {
            item.items.forEach { subItem ->
                CollectionItemNode(
                    item = subItem,
                    depth = depth + 1,
                    onRequestClick = onRequestClick,
                    selectedCollectionItemId = selectedCollectionItemId,
                    onCreateFolder = onCreateFolder,
                    onRenameItem = onRenameItem,
                    onDeleteItem = onDeleteItem,
                    onRemoveFromFolder = onRemoveFromFolder,
                    dragCallbacks = dragCallbacks,
                )
            }
        }
    }

    // Dialogs
    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New Folder",
            label = "Folder Name",
            confirmText = "Create",
            onConfirm = { name ->
                onCreateFolder(item.id, name)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename Folder",
            label = "Folder Name",
            initialValue = item.name,
            confirmText = "Rename",
            onConfirm = { newName ->
                onRenameItem(item, newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Delete Folder",
            message = "Are you sure you want to delete '${item.name}' and all its contents?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteItem(item)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun ItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddFolder: (() -> Unit)? = null,
    onRemoveFromFolder: (() -> Unit)? = null,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (onAddFolder != null) {
            DropdownMenuItem(
                text = { Text("Add Folder") },
                onClick = onAddFolder,
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
            )
        }
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        )
        if (onRemoveFromFolder != null) {
            DropdownMenuItem(
                text = { Text("Remove from Folder") },
                onClick = onRemoveFromFolder,
                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = onDelete,
            leadingIcon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }
}
