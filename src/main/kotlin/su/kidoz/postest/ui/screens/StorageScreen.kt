package su.kidoz.postest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.data.security.StorageMode
import su.kidoz.postest.viewmodel.StorageState

@Composable
fun StorageScreen(
    state: StorageState,
    onRetry: () -> Unit,
    onRecover: (String) -> Unit,
    onContinue: () -> Unit,
    onChooseMode: (StorageMode) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (state == StorageState.ChooseMode) "Choose storage protection" else "Storage",
                    style = MaterialTheme.typography.headlineMedium,
                )
                when (state) {
                    StorageState.ChooseMode -> {
                        StorageModeChoice(onChooseMode)
                    }

                    StorageState.Opening -> {
                        CircularProgressIndicator()
                        Text("Opening your workspace…")
                    }

                    is StorageState.Blocked -> {
                        var key by remember(state) { mutableStateOf("") }
                        Text(state.problem.description)
                        Text(
                            "Postest has not started the workspace or autosave. " +
                                "You can retry, recover with a saved key, or close this window.",
                        )
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it },
                            label = { Text("Recovery key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onRetry) { Text("Retry unlocking") }
                            TextButton(onClick = {
                                val recovery = key
                                key = ""
                                onRecover(recovery)
                            }, enabled = key.isNotBlank()) {
                                Text("Recover storage")
                            }
                        }
                    }

                    is StorageState.RecoverySetup -> {
                        RecoveryKeyContent(state.key)
                        Button(onClick = onContinue) { Text("I saved my recovery key") }
                    }

                    StorageState.Ready -> {}
                }
            }
        }
    }
}

@Composable
private fun StorageModeChoice(onChooseMode: (StorageMode) -> Unit) {
    var selected by remember { mutableStateOf(StorageMode.ENCRYPTED) }
    Text("Choose how Postest saves requests, environments, and history on this computer.")
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StorageModeOption(
            title = "Encrypted (recommended)",
            description =
                "Protects saved data using your OS keychain. " +
                    "Save a recovery key to restore access if the keychain entry is lost.",
            selected = selected == StorageMode.ENCRYPTED,
            onSelect = { selected = StorageMode.ENCRYPTED },
        )
        StorageModeOption(
            title = "Unencrypted",
            description =
                "No keychain or recovery key required. " +
                    "Anyone who can read the database file can read saved passwords, tokens, and responses.",
            selected = selected == StorageMode.UNENCRYPTED,
            onSelect = { selected = StorageMode.UNENCRYPTED },
        )
    }
    Text("This choice is saved for this workspace.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onChooseMode(selected) }) { Text("Create workspace") }
}

@Composable
private fun StorageModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun RecoveryKeyContent(key: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Save this recovery key in a password manager, separately from your database backup.")
        SelectionContainer { Text(key, style = MaterialTheme.typography.bodyLarge) }
        Text(
            "Anyone with this key and your database can read its contents. " +
                "If the OS credential entry is lost, this key is required to recover your data.",
        )
        Text(
            "Back up posttest.db while Postest is closed. " +
                "Keep this key with your recovery records when moving to another computer.",
        )
    }
}
