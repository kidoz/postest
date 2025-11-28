package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.Environment

@Composable
fun EnvironmentSelector(
    environments: List<Environment>,
    selectedEnvironmentId: String?,
    onEnvironmentSelect: (String?) -> Unit,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedEnvironment = environments.find { it.id == selectedEnvironmentId }

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selectedEnvironment?.name ?: "No Environment",
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(200.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("No Environment") },
                    onClick = {
                        onEnvironmentSelect(null)
                        expanded = false
                    },
                )

                if (environments.isNotEmpty()) {
                    HorizontalDivider()

                    environments.forEach { environment ->
                        DropdownMenuItem(
                            text = { Text(environment.name) },
                            onClick = {
                                onEnvironmentSelect(environment.id)
                                expanded = false
                            },
                            leadingIcon =
                                if (environment.id == selectedEnvironmentId) {
                                    {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Manage Environments") },
                    onClick = {
                        onManageEnvironments()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }

        IconButton(onClick = onManageEnvironments) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Manage environments",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
