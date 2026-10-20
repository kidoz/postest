package su.kidoz.postest.ui.components.request

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CertificatePin
import su.kidoz.postest.domain.model.CertificatePinMode
import su.kidoz.postest.domain.model.OAuth2GrantType
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuthPanelTest {
    @Test
    fun `OAuth2 panel authorizes and clears a stored token`() =
        runComposeUiTest {
            var auth by
                mutableStateOf<AuthConfig?>(
                    AuthConfig.OAuth2(
                        grantType = OAuth2GrantType.CLIENT_CREDENTIALS,
                        tokenUrl = "https://provider.test/token",
                        clientId = "client",
                        accessToken = "token-1",
                        expiresAt = System.currentTimeMillis() + 3_600_000,
                    ),
                )
            var authorized: AuthConfig.OAuth2? = null
            setContent {
                AppTheme {
                    AuthPanel(
                        auth = auth,
                        clientCertificate = null,
                        onAuthChange = { auth = it },
                        onClientCertChange = {},
                        onAuthorizeOAuth2 = { authorized = it },
                    )
                }
            }

            onNodeWithText("OAuth 2.0").assertIsSelected()
            onNodeWithText("Access token stored", substring = true).assertExists()
            onNodeWithTag("oauth2-authorize").assertIsEnabled().performClick()
            assertEquals("client", authorized?.clientId)

            onNodeWithText("Clear Token").performClick()
            runOnIdle {
                val cleared = auth as AuthConfig.OAuth2
                assertTrue(cleared.accessToken.isBlank())
                assertEquals(0, cleared.expiresAt)
            }
        }

    @Test
    fun `certificate pin section toggles and changes mode`() =
        runComposeUiTest {
            var pin by mutableStateOf<CertificatePin?>(null)
            setContent {
                AppTheme {
                    AuthPanel(
                        auth = AuthConfig.None,
                        clientCertificate = null,
                        onAuthChange = {},
                        onClientCertChange = {},
                        certificatePin = pin,
                        onCertificatePinChange = { pin = it },
                    )
                }
            }

            onNodeWithTag("certificate-pin").performClick()
            runOnIdle { assertNotNull(pin) }
            onNodeWithText("Public Key").performClick()
            runOnIdle { assertEquals(CertificatePinMode.PUBLIC_KEY, pin?.mode) }
        }
}
