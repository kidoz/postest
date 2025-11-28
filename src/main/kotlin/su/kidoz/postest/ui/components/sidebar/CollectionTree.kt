package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.ui.theme.AppTheme

@Composable
fun CollectionTree(
    collections: List<RequestCollection>,
    onRequestClick: (CollectionItem.Request) -> Unit,
    onNewCollection: () -> Unit,
    onNewRequest: (RequestCollection) -> Unit,
    onImportCollection: () -> Unit,
    onExportCollection: (RequestCollection) -> Unit,
    onDeleteCollection: (RequestCollection) -> Unit,
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
                        onDeleteCollection = { onDeleteCollection(collection) },
                        onDeleteItem = onDeleteItem,
                    )
                }
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
    onDeleteCollection: () -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

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

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Request") },
                        onClick = {
                            showMenu = false
                            onNewRequest()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export to Postman") },
                        onClick = {
                            showMenu = false
                            onExportCollection()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete Collection", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
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
        }

        if (expanded) {
            collection.items.forEach { item ->
                CollectionItemNode(
                    item = item,
                    depth = 1,
                    onRequestClick = onRequestClick,
                    onDeleteItem = onDeleteItem,
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Collection") },
            text = { Text("Are you sure you want to delete '${collection.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCollection()
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CollectionItemNode(
    item: CollectionItem,
    depth: Int,
    onRequestClick: (CollectionItem.Request) -> Unit,
    onDeleteItem: (CollectionItem) -> Unit,
) {
    val extendedColors = AppTheme.extendedColors
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    when (item) {
        is CollectionItem.Request -> {
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

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            },
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
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Request") },
                    text = { Text("Are you sure you want to delete '${item.name}'?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteItem(item)
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }

        is CollectionItem.Folder -> {
            var expanded by remember { mutableStateOf(true) }

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

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Folder", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = true
                                },
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
                }

                if (expanded) {
                    item.items.forEach { subItem ->
                        CollectionItemNode(
                            item = subItem,
                            depth = depth + 1,
                            onRequestClick = onRequestClick,
                            onDeleteItem = onDeleteItem,
                        )
                    }
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Folder") },
                    text = { Text("Are you sure you want to delete '${item.name}' and all its contents?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteItem(item)
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}
