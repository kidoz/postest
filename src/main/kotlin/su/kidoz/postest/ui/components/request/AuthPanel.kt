package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.AuthConfig

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
    onAuthChange: (AuthConfig?) -> Unit,
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

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        // Auth type selector
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
