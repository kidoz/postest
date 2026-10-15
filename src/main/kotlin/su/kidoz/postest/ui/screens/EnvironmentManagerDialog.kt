package su.kidoz.postest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.ui.screens.environment.EnvironmentDetails
import su.kidoz.postest.ui.screens.environment.EnvironmentEditor
import su.kidoz.postest.ui.screens.environment.EnvironmentList

/**
 * Dialog for managing environments.
 * Coordinates between list, details, and editor views.
 */
@Composable
fun EnvironmentManagerDialog(
    environments: List<Environment>,
    activeEnvironmentId: String?,
    onDismiss: () -> Unit,
    onSave: (Environment) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
) {
    var selectedEnvironment by remember { mutableStateOf<Environment?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var editingEnvironment by remember { mutableStateOf<Environment?>(null) }

    fun startEditing(environment: Environment?) {
        isCreating = environment == null
        editingEnvironment = environment ?: Environment(name = "")
        isEditing = true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier =
                Modifier
                    .width(960.dp)
                    .height(640.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    // Left panel: Environment list
                    EnvironmentListPanel(
                        environments = environments,
                        activeEnvironmentId = activeEnvironmentId,
                        selectedId = selectedEnvironment?.id,
                        onSelect = { selectedEnvironment = it },
                        onEdit = { startEditing(it) },
                        onDelete = { onDelete(it.id) },
                        onAdd = { startEditing(null) },
                    )

                    VerticalDivider()

                    // Right panel: Details or Editor
                    RightPanel(
                        isEditing = isEditing,
                        isCreating = isCreating,
                        editingEnvironment = editingEnvironment,
                        selectedEnvironment = selectedEnvironment,
                        isActive = selectedEnvironment?.id == activeEnvironmentId,
                        hasEnvironments = environments.isNotEmpty(),
                        onSave = { env ->
                            onSave(env)
                            isEditing = false
                            editingEnvironment = null
                            selectedEnvironment = env
                        },
                        onCancel = {
                            isEditing = false
                            editingEnvironment = null
                        },
                        onEdit = { startEditing(selectedEnvironment) },
                        onSetActive = { selectedEnvironment?.id?.let(onSetActive) },
                        onAdd = { startEditing(null) },
                    )
                }

                HorizontalDivider()

                TextButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun EnvironmentListPanel(
    environments: List<Environment>,
    activeEnvironmentId: String?,
    selectedId: String?,
    onSelect: (Environment) -> Unit,
    onEdit: (Environment) -> Unit,
    onDelete: (Environment) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.width(240.dp - 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Environments",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add environment")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (environments.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No environments yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            EnvironmentList(
                environments = environments,
                activeEnvironmentId = activeEnvironmentId,
                selectedId = selectedId,
                onSelect = onSelect,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RowScope.RightPanel(
    isEditing: Boolean,
    isCreating: Boolean,
    editingEnvironment: Environment?,
    selectedEnvironment: Environment?,
    isActive: Boolean,
    hasEnvironments: Boolean,
    onSave: (Environment) -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp),
    ) {
        when {
            isEditing && editingEnvironment != null -> {
                EnvironmentEditor(
                    environment = editingEnvironment,
                    isCreating = isCreating,
                    modifier = Modifier.fillMaxSize(),
                    onSave = onSave,
                    onCancel = onCancel,
                )
            }

            selectedEnvironment != null -> {
                EnvironmentDetails(
                    environment = selectedEnvironment,
                    isActive = isActive,
                    modifier = Modifier.fillMaxSize(),
                    onEdit = onEdit,
                    onSetActive = onSetActive,
                )
            }

            else -> {
                EmptyDetailsPane(hasEnvironments = hasEnvironments, onAdd = onAdd)
            }
        }
    }
}

@Composable
private fun EmptyDetailsPane(
    hasEnvironments: Boolean,
    onAdd: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(40.dp),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "No environment selected",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text =
                    if (hasEnvironments) {
                        "Select an environment to view its variables"
                    } else {
                        "Create an environment to store variables like baseUrl"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (!hasEnvironments) {
                Spacer(Modifier.height(16.dp))

                Button(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New Environment")
                }
            }
        }
    }
}
