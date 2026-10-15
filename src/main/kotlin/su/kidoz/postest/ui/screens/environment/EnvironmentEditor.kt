package su.kidoz.postest.ui.screens.environment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import su.kidoz.postest.ui.components.common.AppTooltip
import su.kidoz.postest.ui.components.common.CompactTextField
import su.kidoz.postest.ui.components.common.DesktopIconButton
import su.kidoz.postest.ui.theme.MonospaceFont

/**
 * Editor for creating/editing an environment.
 */
@Composable
fun EnvironmentEditor(
    environment: Environment,
    onSave: (Environment) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isCreating: Boolean = false,
) {
    var name by remember(environment.id) { mutableStateOf(environment.name) }
    var variables by remember(environment.id) { mutableStateOf(environment.variables) }

    Column(modifier = modifier) {
        Text(
            text = if (isCreating) "Create Environment" else "Edit Environment",
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
                Text(if (isCreating) "Create" else "Save")
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= 480.dp
        Column(Modifier.fillMaxSize()) {
            if (wide) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Spacer(Modifier.width(32.dp))
                    Text("Name", Modifier.weight(0.4f).padding(start = 12.dp), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("Value", Modifier.weight(0.6f).padding(start = 12.dp), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(72.dp))
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(variables, key = { _, variable -> variable.id }) { index, variable ->
                    VariableRow(
                        variable = variable,
                        wide = wide,
                        onVariableChange = { updated ->
                            onVariablesChange(variables.toMutableList().apply { this[index] = updated })
                        },
                        onDelete = { onVariablesChange(variables.filterIndexed { i, _ -> i != index }) },
                    )
                }
            }
            if (variables.isEmpty()) {
                Text(
                    "Add variables such as baseUrl, then use {{baseUrl}} in a request.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            TextButton(onClick = { onVariablesChange(variables + Variable(key = "", value = "")) }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add variable")
            }
        }
    }
}

@Composable
private fun VariableRow(
    variable: Variable,
    wide: Boolean,
    onVariableChange: (Variable) -> Unit,
    onDelete: () -> Unit,
) {
    val fields: @Composable RowScope.() -> Unit = {
        CompactTextField(
            value = variable.key,
            onValueChange = { onVariableChange(variable.copy(key = it)) },
            label = "Variable name",
            modifier = Modifier.weight(0.4f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFont),
        )
        Spacer(Modifier.width(8.dp))
        CompactTextField(
            value = variable.value,
            onValueChange = { onVariableChange(variable.copy(value = it)) },
            label = "Variable value",
            modifier = Modifier.weight(0.6f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonospaceFont),
            visualTransformation = if (variable.isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
    val enabled: @Composable () -> Unit = {
        AppTooltip("Enable variable") {
            Checkbox(
                checked = variable.enabled,
                onCheckedChange = { onVariableChange(variable.copy(enabled = it)) },
                modifier = Modifier.size(32.dp).semantics { contentDescription = "Enable variable" },
            )
        }
    }
    val actions: @Composable () -> Unit = {
        DesktopIconButton(
            icon = if (variable.isSecret) Icons.Default.Lock else Icons.Default.LockOpen,
            description = if (variable.isSecret) "Mark as regular variable" else "Mark as secret variable",
            onClick = {
                onVariableChange(variable.copy(type = if (variable.isSecret) VariableType.DEFAULT else VariableType.SECRET))
            },
            tint = if (variable.isSecret) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DesktopIconButton(Icons.Default.Delete, "Remove variable", onDelete, tint = MaterialTheme.colorScheme.error)
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            enabled()
            fields()
            Spacer(Modifier.width(8.dp))
            actions()
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                enabled()
                Text(if (variable.enabled) "Enabled" else "Disabled", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                actions()
            }
            Row(Modifier.fillMaxWidth(), content = fields)
        }
    }
}
