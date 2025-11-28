package su.kidoz.postest.ui.components.common

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TextInputDialogTest {
    @Test
    fun `dialog shows title and label`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test Title",
                    label = "Test Label",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Test Title").assertExists()
            onNodeWithText("Test Label").assertExists()
        }

    @Test
    fun `dialog shows initial value in text field`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    initialValue = "Initial Text",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Initial Text").assertExists()
        }

    @Test
    fun `confirm button uses custom text`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    confirmText = "Create",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Create").assertExists()
        }

    @Test
    fun `cancel button is always visible`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Cancel").assertExists()
        }

    @Test
    fun `confirm button is disabled when text is blank`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    initialValue = "",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Confirm").assertIsNotEnabled()
        }

    @Test
    fun `confirm button is enabled when text is not blank`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    initialValue = "Valid Input",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Confirm").assertIsEnabled()
        }

    @Test
    fun `confirm callback is called with entered text`() =
        runComposeUiTest {
            var confirmedText: String? = null

            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    initialValue = "",
                    onConfirm = { confirmedText = it },
                    onDismiss = {},
                )
            }

            onNodeWithText("Name").performTextInput("My Input")
            onNodeWithText("Confirm").performClick()

            assertEquals("My Input", confirmedText)
        }

    @Test
    fun `dismiss callback is called when cancel clicked`() =
        runComposeUiTest {
            var dismissed = false

            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }

            onNodeWithText("Cancel").performClick()

            assertTrue(dismissed)
        }

    @Test
    fun `custom validator disables confirm button for invalid input`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Email",
                    initialValue = "invalid-email",
                    onConfirm = {},
                    onDismiss = {},
                    validator = { it.contains("@") },
                )
            }

            onNodeWithText("Confirm").assertIsNotEnabled()
        }

    @Test
    fun `custom validator enables confirm button for valid input`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Email",
                    initialValue = "valid@email.com",
                    onConfirm = {},
                    onDismiss = {},
                    validator = { it.contains("@") },
                )
            }

            onNodeWithText("Confirm").assertIsEnabled()
        }

    @Test
    fun `text field updates enable state when typing`() =
        runComposeUiTest {
            setContent {
                TextInputDialog(
                    title = "Test",
                    label = "Name",
                    initialValue = "",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            // Initially disabled
            onNodeWithText("Confirm").assertIsNotEnabled()

            // Type something
            onNodeWithText("Name").performTextInput("Hello")

            // Now enabled
            onNodeWithText("Confirm").assertIsEnabled()
        }
}
