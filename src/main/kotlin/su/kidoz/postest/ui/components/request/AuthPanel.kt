package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.ClientCertConfig

enum class AuthType(
    val displayName: String,
) {
    NONE("No Auth"),
    BASIC("Basic Auth"),
    BEARER("Bearer Token"),
    API_KEY("API Key"),
}

@Composable
fun AuthPanel(
    auth: AuthConfig?,
    clientCertificate: ClientCertConfig?,
    onAuthChange: (AuthConfig?) -> Unit,
    onClientCertChange: (ClientCertConfig?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember {
        mutableStateOf(
            when (auth) {
                is AuthConfig.Basic -> AuthType.BASIC
                is AuthConfig.Bearer -> AuthType.BEARER
                is AuthConfig.ApiKey -> AuthType.API_KEY
                else -> AuthType.NONE
            },
        )
    }

    var certEnabled by remember { mutableStateOf(clientCertificate != null) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
        // Client Certificate section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = certEnabled,
                onCheckedChange = { checked ->
                    certEnabled = checked
                    if (checked) {
                        onClientCertChange(clientCertificate ?: ClientCertConfig(""))
                    } else {
                        onClientCertChange(null)
                    }
                },
            )
            Text("Client Certificate (mTLS)", style = MaterialTheme.typography.titleSmall)
        }

        if (certEnabled) {
            val certConfig = clientCertificate ?: ClientCertConfig("")
            val certPath = certConfig.certPath
            val isPkcs12 =
                certPath.lowercase().let { it.endsWith(".p12") || it.endsWith(".pfx") }

            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "PEM (.pem, .crt + .key) or PKCS#12 (.p12, .pfx)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = certPath,
                    onValueChange = { path ->
                        onClientCertChange(certConfig.copy(certPath = path))
                    },
                    label = { Text("Certificate file") },
                    placeholder = { Text("/path/to/client.pem or client.p12") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                if (!isPkcs12) {
                    OutlinedTextField(
                        value = certConfig.keyPath,
                        onValueChange = { path ->
                            onClientCertChange(certConfig.copy(keyPath = path))
                        },
                        label = { Text("Private key file") },
                        placeholder = { Text("/path/to/client.key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                OutlinedTextField(
                    value = certConfig.passphrase,
                    onValueChange = { pass ->
                        onClientCertChange(certConfig.copy(passphrase = pass))
                    },
                    label = { Text("Passphrase") },
                    placeholder = { Text("Certificate password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }

        Spacer(Modifier.height(12.dp))

        // HTTP auth type selector
        Text("Authorization", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        when (type) {
                            AuthType.NONE -> onAuthChange(AuthConfig.None)
                            AuthType.BASIC -> onAuthChange(AuthConfig.Basic("", ""))
                            AuthType.BEARER -> onAuthChange(AuthConfig.Bearer(""))
                            AuthType.API_KEY -> onAuthChange(AuthConfig.ApiKey("", "", AuthConfig.ApiKey.AddTo.HEADER))
                        }
                    },
                    label = { Text(type.displayName) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Auth fields
        when (selectedType) {
            AuthType.NONE -> {
                Text(
                    text = "This request does not use any authorization.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AuthType.BASIC -> {
                val basicAuth = auth as? AuthConfig.Basic
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = basicAuth?.username ?: "",
                        onValueChange = { username ->
                            onAuthChange(AuthConfig.Basic(username, basicAuth?.password ?: ""))
                        },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = basicAuth?.password ?: "",
                        onValueChange = { password ->
                            onAuthChange(AuthConfig.Basic(basicAuth?.username ?: "", password))
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }

            AuthType.BEARER -> {
                val bearerAuth = auth as? AuthConfig.Bearer
                OutlinedTextField(
                    value = bearerAuth?.token ?: "",
                    onValueChange = { token ->
                        onAuthChange(AuthConfig.Bearer(token))
                    },
                    label = { Text("Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            AuthType.API_KEY -> {
                val apiKeyAuth = auth as? AuthConfig.ApiKey
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = apiKeyAuth?.key ?: "",
                        onValueChange = { key ->
                            onAuthChange(
                                AuthConfig.ApiKey(
                                    key,
                                    apiKeyAuth?.value ?: "",
                                    apiKeyAuth?.addTo ?: AuthConfig.ApiKey.AddTo.HEADER,
                                ),
                            )
                        },
                        label = { Text("Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = apiKeyAuth?.value ?: "",
                        onValueChange = { value ->
                            onAuthChange(
                                AuthConfig.ApiKey(
                                    apiKeyAuth?.key ?: "",
                                    value,
                                    apiKeyAuth?.addTo ?: AuthConfig.ApiKey.AddTo.HEADER,
                                ),
                            )
                        },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add to:", modifier = Modifier.padding(top = 8.dp))
                        FilterChip(
                            selected = apiKeyAuth?.addTo == AuthConfig.ApiKey.AddTo.HEADER,
                            onClick = {
                                onAuthChange(
                                    AuthConfig.ApiKey(
                                        apiKeyAuth?.key ?: "",
                                        apiKeyAuth?.value ?: "",
                                        AuthConfig.ApiKey.AddTo.HEADER,
                                    ),
                                )
                            },
                            label = { Text("Header") },
                        )
                        FilterChip(
                            selected = apiKeyAuth?.addTo == AuthConfig.ApiKey.AddTo.QUERY_PARAM,
                            onClick = {
                                onAuthChange(
                                    AuthConfig.ApiKey(
                                        apiKeyAuth?.key ?: "",
                                        apiKeyAuth?.value ?: "",
                                        AuthConfig.ApiKey.AddTo.QUERY_PARAM,
                                    ),
                                )
                            },
                            label = { Text("Query Param") },
                        )
                    }
                }
            }
        }
    }
}
