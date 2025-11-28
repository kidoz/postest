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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    onDismiss: () -> Unit,
    onSave: (Environment) -> Unit,
    onDelete: (String) -> Unit,
) {
    var selectedEnvironment by remember { mutableStateOf<Environment?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editingEnvironment by remember { mutableStateOf<Environment?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                Modifier
                    .width(960.dp)
                    .height(640.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left panel: Environment list
                EnvironmentListPanel(
                    environments = environments,
                    selectedId = selectedEnvironment?.id,
                    onSelect = { selectedEnvironment = it },
                    onEdit = {
                        editingEnvironment = it
                        isEditing = true
                    },
                    onDelete = { onDelete(it.id) },
                    onAdd = {
                        editingEnvironment = Environment(name = "New Environment")
                        isEditing = true
                    },
                    onClose = onDismiss,
                )

                VerticalDivider()

                // Right panel: Details or Editor
                RightPanel(
                    isEditing = isEditing,
                    editingEnvironment = editingEnvironment,
                    selectedEnvironment = selectedEnvironment,
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
                    onEdit = {
                        editingEnvironment = selectedEnvironment
                        isEditing = true
                    },
                )
            }
        }
    }
}

@Composable
private fun EnvironmentListPanel(
    environments: List<Environment>,
    selectedId: String?,
    onSelect: (Environment) -> Unit,
    onEdit: (Environment) -> Unit,
    onDelete: (Environment) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
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

        EnvironmentList(
            environments = environments,
            selectedId = selectedId,
            onSelect = onSelect,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onClose) {
            Text("Close")
        }
    }
}

@Composable
private fun RowScope.RightPanel(
    isEditing: Boolean,
    editingEnvironment: Environment?,
    selectedEnvironment: Environment?,
    onSave: (Environment) -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
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
                    modifier = Modifier.fillMaxSize(),
                    onSave = onSave,
                    onCancel = onCancel,
                )
            }

            selectedEnvironment != null -> {
                EnvironmentDetails(
                    environment = selectedEnvironment,
                    modifier = Modifier.fillMaxSize(),
                    onEdit = onEdit,
                )
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Select or create an environment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
