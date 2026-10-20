package su.kidoz.postest.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import su.kidoz.postest.viewmodel.StorageState

@Composable
fun StorageScreen(
    state: StorageState,
    onRetry: () -> Unit,
    onRecover: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.widthIn(max = 620.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Protected storage", style = MaterialTheme.typography.headlineMedium)
                when (state) {
                    StorageState.Opening -> {
                        CircularProgressIndicator()
                        Text("Opening protected storage. Your operating system may ask you to unlock its credential store.")
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
fun RecoveryKeyContent(key: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Save this recovery key in a password manager, separately from your database backup.")
        SelectionContainer { Text(key, style = MaterialTheme.typography.bodyLarge) }
        Text(
            "Anyone with this key and your database can read its contents. " +
                "If the OS credential entry is lost, this key is required to recover your data.",
        )
        Text(
            "Back up postest-protected.db while Postest is closed. " +
                "Keep this key with your recovery records when moving to another computer.",
        )
    }
}
