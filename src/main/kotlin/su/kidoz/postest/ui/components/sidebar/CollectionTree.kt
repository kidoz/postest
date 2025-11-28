package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.ui.components.common.ConfirmDialog
import su.kidoz.postest.ui.components.common.TextInputDialog
import su.kidoz.postest.ui.theme.AppTheme

@Composable
fun CollectionTree(
    collections: List<RequestCollection>,
    onRequestClick: (CollectionItem.Request) -> Unit,
    onNewCollection: () -> Unit,
    onNewRequest: (RequestCollection) -> Unit,
    onImportCollection: () -> Unit,
    onExportCollection: (RequestCollection) -> Unit,
    onRenameCollection: (RequestCollection, String) -> Unit,
    onDeleteCollection: (RequestCollection) -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

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
                text = "Collections",
                style = MaterialTheme.typography.titleSmall,
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Collection actions",
                        modifier = Modifier.size(20.dp),
                    )
                }

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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(collections) { collection ->
                    CollectionNode(
                        collection = collection,
                        onRequestClick = onRequestClick,
                        onNewRequest = { onNewRequest(collection) },
                        onExportCollection = { onExportCollection(collection) },
                        onRenameCollection = { newName -> onRenameCollection(collection, newName) },
                        onDeleteCollection = { onDeleteCollection(collection) },
                        onRenameItem = onRenameItem,
                        onDeleteItem = onDeleteItem,
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
    onNewRequest: () -> Unit,
    onExportCollection: () -> Unit,
    onRenameCollection: (String) -> Unit,
    onDeleteCollection: () -> Unit,
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
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
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Collection menu",
                        modifier = Modifier.size(16.dp),
                    )
                }

                CollectionContextMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onAddRequest = {
                        showMenu = false
                        onNewRequest()
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
                    onRenameItem = onRenameItem,
                    onDeleteItem = onDeleteItem,
                )
            }
        }
    }

    // Dialogs
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
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
) {
    when (item) {
        is CollectionItem.Request ->
            RequestItemNode(
                item = item,
                depth = depth,
                onRequestClick = onRequestClick,
                onRename = { newName -> onRenameItem(item, newName) },
                onDelete = { onDeleteItem(item) },
            )
        is CollectionItem.Folder ->
            FolderItemNode(
                item = item,
                depth = depth,
                onRequestClick = onRequestClick,
                onRenameItem = onRenameItem,
                onDeleteItem = onDeleteItem,
            )
    }
}

@Composable
private fun RequestItemNode(
    item: CollectionItem.Request,
    depth: Int,
    onRequestClick: (CollectionItem.Request) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
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
                .clickable { onRequestClick(item) }
                .padding(start = (depth * 16 + 8).dp, top = 4.dp, bottom = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.request.method.name,
            style = MaterialTheme.typography.labelSmall,
            color = methodColor,
            modifier = Modifier.width(48.dp),
        )

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(16.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Request menu",
                    modifier = Modifier.size(14.dp),
                )
            }

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
    onRenameItem: (CollectionItem, String) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
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
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Folder menu",
                        modifier = Modifier.size(14.dp),
                    )
                }

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
                )
            }
        }

        if (expanded) {
            item.items.forEach { subItem ->
                CollectionItemNode(
                    item = subItem,
                    depth = depth + 1,
                    onRequestClick = onRequestClick,
                    onRenameItem = onRenameItem,
                    onDeleteItem = onDeleteItem,
                )
            }
        }
    }

    // Dialogs
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
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = onRename,
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
        )
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
