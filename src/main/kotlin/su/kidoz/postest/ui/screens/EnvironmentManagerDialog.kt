package su.kidoz.postest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType

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
                // Environment list
                Column(
                    modifier =
                        Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Environments",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        IconButton(
                            onClick = {
                                editingEnvironment = Environment(name = "New Environment")
                                isEditing = true
                            },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add environment")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(environments) { environment ->
                            EnvironmentListItem(
                                environment = environment,
                                isSelected = selectedEnvironment?.id == environment.id,
                                onClick = { selectedEnvironment = environment },
                                onEdit = {
                                    editingEnvironment = environment
                                    isEditing = true
                                },
                                onDelete = { onDelete(environment.id) },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                VerticalDivider()

                // Environment details or editor
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                ) {
                    // Capture vars in local vals to enable smart-casting
                    val currentEditingEnv = editingEnvironment
                    val currentSelectedEnv = selectedEnvironment

                    when {
                        isEditing && currentEditingEnv != null -> {
                            EnvironmentEditor(
                                environment = currentEditingEnv,
                                modifier = Modifier.fillMaxSize(),
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
                            )
                        }

                        currentSelectedEnv != null -> {
                            EnvironmentDetails(
                                environment = currentSelectedEnv,
                                modifier = Modifier.fillMaxSize(),
                                onEdit = {
                                    editingEnvironment = selectedEnvironment
                                    isEditing = true
                                },
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
        }
    }
}

@Composable
private fun EnvironmentListItem(
    environment: Environment,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = environment.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentDetails(
    environment: Environment,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = environment.name,
                style = MaterialTheme.typography.titleLarge,
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Variables (${environment.variables.size})",
            style = MaterialTheme.typography.titleSmall,
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(environment.variables) { variable ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = variable.key,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (variable.type == VariableType.SECRET) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Secret",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            text = if (variable.type == VariableType.SECRET) "••••••" else variable.value,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentEditor(
    environment: Environment,
    modifier: Modifier = Modifier,
    onSave: (Environment) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(environment.name) }
    var variables by remember { mutableStateOf(environment.variables) }

    Column(modifier = modifier) {
        Text(
            text = if (environment.id.isBlank()) "New Environment" else "Edit Environment",
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Environment Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Variables",
            style = MaterialTheme.typography.titleSmall,
        )

        VariableEditor(
            variables = variables,
            onVariablesChange = { variables = it },
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    onSave(
                        environment.copy(
                            name = name,
                            variables = variables,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun VariableEditor(
    variables: List<Variable>,
    onVariablesChange: (List<Variable>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(variables, key = { _, variable -> variable.id }) { index, variable ->
                VariableRow(
                    variable = variable,
                    onVariableChange = { newVariable ->
                        onVariablesChange(
                            variables.toMutableList().apply {
                                this[index] = newVariable
                            },
                        )
                    },
                    onDelete = {
                        onVariablesChange(
                            variables.toMutableList().apply {
                                removeAt(index)
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                onVariablesChange(variables + Variable(key = "", value = ""))
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add Variable")
        }
    }
}

@Composable
private fun VariableRow(
    variable: Variable,
    onVariableChange: (Variable) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            // Header row: Checkbox, Secret toggle, Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = variable.enabled,
                        onCheckedChange = { checked ->
                            onVariableChange(variable.copy(enabled = checked))
                        },
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (variable.enabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    // Secret toggle
                    IconButton(
                        onClick = {
                            val newType =
                                if (variable.type == VariableType.SECRET) {
                                    VariableType.DEFAULT
                                } else {
                                    VariableType.SECRET
                                }
                            onVariableChange(variable.copy(type = newType))
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (variable.type == VariableType.SECRET) {
                                    Icons.Default.Lock
                                } else {
                                    Icons.Default.LockOpen
                                },
                            contentDescription =
                                if (variable.type == VariableType.SECRET) {
                                    "Secret variable"
                                } else {
                                    "Regular variable"
                                },
                            tint =
                                if (variable.type == VariableType.SECRET) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Name field - full width
            OutlinedTextField(
                value = variable.key,
                onValueChange = { newKey ->
                    onVariableChange(variable.copy(key = newKey))
                },
                label = { Text("Name") },
                placeholder = { Text("Variable name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))

            // Value field - full width
            OutlinedTextField(
                value = variable.value,
                onValueChange = { newValue ->
                    onVariableChange(variable.copy(value = newValue))
                },
                label = { Text("Value") },
                placeholder = { Text("Variable value") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                visualTransformation =
                    if (variable.type == VariableType.SECRET) {
                        androidx.compose.ui.text.input
                            .PasswordVisualTransformation()
                    } else {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    },
            )
        }
    }
}
