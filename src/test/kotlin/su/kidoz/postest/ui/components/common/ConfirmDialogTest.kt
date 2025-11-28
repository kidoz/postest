package su.kidoz.postest.ui.components.common

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConfirmDialogTest {
    @Test
    fun `dialog shows title and message`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Delete Item",
                    message = "Are you sure you want to delete this item?",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Delete Item").assertExists()
            onNodeWithText("Are you sure you want to delete this item?").assertExists()
        }

    @Test
    fun `dialog shows default confirm text`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Test",
                    message = "Message",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Confirm").assertExists()
        }

    @Test
    fun `dialog shows custom confirm text`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Test",
                    message = "Message",
                    confirmText = "Delete",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Delete").assertExists()
        }

    @Test
    fun `cancel button is always visible`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Test",
                    message = "Message",
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Cancel").assertExists()
        }

    @Test
    fun `confirm callback is called when confirm clicked`() =
        runComposeUiTest {
            var confirmed = false

            setContent {
                ConfirmDialog(
                    title = "Test",
                    message = "Message",
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }

            onNodeWithText("Confirm").performClick()

            assertTrue(confirmed)
        }

    @Test
    fun `dismiss callback is called when cancel clicked`() =
        runComposeUiTest {
            var dismissed = false

            setContent {
                ConfirmDialog(
                    title = "Test",
                    message = "Message",
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }

            onNodeWithText("Cancel").performClick()

            assertTrue(dismissed)
        }

    @Test
    fun `destructive dialog renders correctly`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Delete Collection",
                    message = "This action cannot be undone.",
                    confirmText = "Delete",
                    isDestructive = true,
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            // The dialog should still show all elements
            onNodeWithText("Delete Collection").assertExists()
            onNodeWithText("This action cannot be undone.").assertExists()
            onNodeWithText("Delete").assertExists()
            onNodeWithText("Cancel").assertExists()
        }

    @Test
    fun `non-destructive dialog renders correctly`() =
        runComposeUiTest {
            setContent {
                ConfirmDialog(
                    title = "Save Changes",
                    message = "Do you want to save your changes?",
                    confirmText = "Save",
                    isDestructive = false,
                    onConfirm = {},
                    onDismiss = {},
                )
            }

            onNodeWithText("Save Changes").assertExists()
            onNodeWithText("Do you want to save your changes?").assertExists()
            onNodeWithText("Save").assertExists()
        }
}
