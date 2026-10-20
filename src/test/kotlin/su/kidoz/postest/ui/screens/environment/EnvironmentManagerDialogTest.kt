package su.kidoz.postest.ui.screens.environment

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.ui.screens.EnvironmentManagerDialog
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EnvironmentManagerDialogTest {
    @Test
    fun `editing after activation uses the current environment`() =
        runComposeUiTest {
            val original = Environment(name = "Development")
            val environments = mutableStateOf(listOf(original))
            var saved: Environment? = null
            setContent {
                AppTheme {
                    EnvironmentManagerDialog(
                        environments = environments.value,
                        activeEnvironmentId = environments.value.firstOrNull { it.isActive }?.id,
                        onDismiss = {},
                        onSave = { saved = it },
                        onDelete = {},
                        onSetActive = { id -> environments.value = environments.value.map { it.copy(isActive = it.id == id) } },
                    )
                }
            }

            onNodeWithText("Development").performClick()
            onNodeWithText("Set Active").performClick()
            // The second Edit action is in the selected environment's details pane.
            onAllNodesWithContentDescription("Edit")[1].performClick()
            onNodeWithText("Save").performClick()

            runOnIdle { assertEquals(original.copy(isActive = true), saved) }
        }
}
