package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CertificatePin
import su.kidoz.postest.domain.model.CertificatePinMode
import su.kidoz.postest.domain.model.ClientCertConfig
import su.kidoz.postest.domain.model.OAuth2GrantType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val tokenExpiryFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

enum class AuthType(
    val displayName: String,
) {
    NONE("No Auth"),
    BASIC("Basic Auth"),
    BEARER("Bearer Token"),
    API_KEY("API Key"),
    OAUTH2("OAuth 2.0"),
}

@Composable
fun AuthPanel(
    auth: AuthConfig?,
    clientCertificate: ClientCertConfig?,
    onAuthChange: (AuthConfig?) -> Unit,
    onClientCertChange: (ClientCertConfig?) -> Unit,
    modifier: Modifier = Modifier,
    showClientCertificate: Boolean = true,
    onAuthorizeOAuth2: (AuthConfig.OAuth2) -> Unit = {},
    certificatePin: CertificatePin? = null,
    onCertificatePinChange: (CertificatePin?) -> Unit = {},
) {
    val authType =
        when (auth) {
            is AuthConfig.Basic -> AuthType.BASIC
            is AuthConfig.Bearer -> AuthType.BEARER
            is AuthConfig.ApiKey -> AuthType.API_KEY
            is AuthConfig.OAuth2 -> AuthType.OAUTH2
            else -> AuthType.NONE
        }

    var selectedType by remember(authType) { mutableStateOf(authType) }

    var certEnabled by remember(clientCertificate != null) { mutableStateOf(clientCertificate != null) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
        // Client Certificate section (HTTP only; WebSocket connections do not apply mTLS config)
        if (showClientCertificate) {
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
        }

        Spacer(Modifier.height(12.dp))

        // Certificate pinning applies to both HTTP and WebSocket requests.
        val pinEnabled = certificatePin != null
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = pinEnabled,
                onCheckedChange = { enabled ->
                    onCertificatePinChange(if (enabled) certificatePin ?: CertificatePin() else null)
                },
                modifier = Modifier.testTag("certificate-pin"),
            )
            Text("Certificate Pinning", style = MaterialTheme.typography.titleSmall)
        }

        if (certificatePin != null) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = certificatePin.mode == CertificatePinMode.CERTIFICATE,
                        onClick = { onCertificatePinChange(certificatePin.copy(mode = CertificatePinMode.CERTIFICATE)) },
                        label = { Text("Certificate") },
                    )
                    FilterChip(
                        selected = certificatePin.mode == CertificatePinMode.PUBLIC_KEY,
                        onClick = { onCertificatePinChange(certificatePin.copy(mode = CertificatePinMode.PUBLIC_KEY)) },
                        label = { Text("Public Key") },
                    )
                }
                OutlinedTextField(
                    value = certificatePin.sha256,
                    onValueChange = { onCertificatePinChange(certificatePin.copy(sha256 = it)) },
                    label = { Text("SHA-256 pin") },
                    placeholder = { Text("Base64, hex, or sha256/...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "Pins the leaf certificate or public key. Base64, hex, and sha256/ values are accepted; " +
                        "normal TLS chain validation still applies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }

        Spacer(Modifier.height(12.dp))

        // HTTP auth type selector
        Text("Authorization", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthType.entries.forEach { type ->
                FilterChip(
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        when (type) {
                            AuthType.NONE -> onAuthChange(AuthConfig.None)
                            AuthType.BASIC -> onAuthChange(AuthConfig.Basic("", ""))
                            AuthType.BEARER -> onAuthChange(AuthConfig.Bearer(""))
                            AuthType.API_KEY -> onAuthChange(AuthConfig.ApiKey("", "", AuthConfig.ApiKey.AddTo.HEADER))
                            AuthType.OAUTH2 -> onAuthChange(AuthConfig.OAuth2())
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

            AuthType.OAUTH2 -> {
                val config = auth as? AuthConfig.OAuth2 ?: AuthConfig.OAuth2()

                fun update(updated: AuthConfig.OAuth2) = onAuthChange(updated)
                val canAuthorize =
                    config.tokenUrl.isNotBlank() &&
                        config.clientId.isNotBlank() &&
                        (config.grantType != OAuth2GrantType.AUTHORIZATION_CODE || config.authorizationUrl.isNotBlank())
                val now = System.currentTimeMillis()
                val status =
                    when {
                        config.accessToken.isBlank() -> {
                            "No access token yet."
                        }

                        config.expiresAt > 0 && now >= config.expiresAt -> {
                            "Access token expired. Authorize again."
                        }

                        config.expiresAt > 0 -> {
                            "Access token stored; expires " +
                                tokenExpiryFormatter.format(Instant.ofEpochMilli(config.expiresAt)) + "."
                        }

                        else -> {
                            "Access token stored."
                        }
                    }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.grantType == OAuth2GrantType.AUTHORIZATION_CODE,
                            onClick = { update(config.copy(grantType = OAuth2GrantType.AUTHORIZATION_CODE)) },
                            label = { Text("Authorization Code") },
                        )
                        FilterChip(
                            selected = config.grantType == OAuth2GrantType.CLIENT_CREDENTIALS,
                            onClick = { update(config.copy(grantType = OAuth2GrantType.CLIENT_CREDENTIALS)) },
                            label = { Text("Client Credentials") },
                        )
                    }

                    if (config.grantType == OAuth2GrantType.AUTHORIZATION_CODE) {
                        OutlinedTextField(
                            value = config.authorizationUrl,
                            onValueChange = { update(config.copy(authorizationUrl = it)) },
                            label = { Text("Authorization URL") },
                            placeholder = { Text("https://provider.example/oauth/authorize") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = config.tokenUrl,
                        onValueChange = { update(config.copy(tokenUrl = it)) },
                        label = { Text("Token URL") },
                        placeholder = { Text("https://provider.example/oauth/token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = config.clientId,
                        onValueChange = { update(config.copy(clientId = it)) },
                        label = { Text("Client ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = config.clientSecret,
                        onValueChange = { update(config.copy(clientSecret = it)) },
                        label = { Text("Client Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    OutlinedTextField(
                        value = config.scope,
                        onValueChange = { update(config.copy(scope = it)) },
                        label = { Text("Scope") },
                        placeholder = { Text("read:items write:items") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = config.clientSecretInBody,
                            onCheckedChange = { update(config.copy(clientSecretInBody = it)) },
                        )
                        Text("Send client secret in request body", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAuthorizeOAuth2(config) },
                            enabled = canAuthorize,
                            modifier = Modifier.testTag("oauth2-authorize"),
                        ) {
                            Text(
                                if (config.grantType == OAuth2GrantType.CLIENT_CREDENTIALS) {
                                    "Get Token"
                                } else {
                                    "Authorize"
                                },
                            )
                        }
                        TextButton(
                            onClick = { update(config.copy(accessToken = "", refreshToken = "", expiresAt = 0)) },
                            enabled = config.accessToken.isNotBlank(),
                        ) {
                            Text("Clear Token")
                        }
                    }

                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (config.expiresAt > 0 && now >= config.expiresAt) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    if (config.grantType == OAuth2GrantType.AUTHORIZATION_CODE) {
                        Text(
                            "Authorization opens your system browser and returns through a local loopback redirect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
