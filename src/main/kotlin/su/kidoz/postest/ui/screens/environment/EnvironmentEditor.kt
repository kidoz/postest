package su.kidoz.postest.ui.screens.environment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType

/**
 * Editor for creating/editing an environment.
 */
@Composable
fun EnvironmentEditor(
    environment: Environment,
    onSave: (Environment) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
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
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
            )
        }
    }
}
