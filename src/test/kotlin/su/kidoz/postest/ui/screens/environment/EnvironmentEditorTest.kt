package su.kidoz.postest.ui.screens.environment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.mockk
import io.mockk.verify
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class EnvironmentEditorTest {
    @Test
    fun `creating an environment uses create labels and saves the variable table`() =
        runComposeUiTest {
            val onSave = mockk<(Environment) -> Unit>(relaxed = true)
            val draft = Environment(name = "")
            setContent {
                AppTheme {
                    Box(Modifier.size(800.dp, 600.dp)) {
                        EnvironmentEditor(draft, onSave, {}, isCreating = true)
                    }
                }
            }
            onNodeWithText("Create Environment").assertExists()
            onNodeWithText("Create").assertIsNotEnabled()
            onNodeWithText("Environment Name").performTextInput("Review")
            onNodeWithText("Add variable").performClick()
            onNodeWithContentDescription("Variable name").performTextInput("baseUrl")
            onNodeWithContentDescription("Variable value").performTextInput("https://example.test")
            onNodeWithText("Create").performClick()
            verify {
                onSave(match { it.id == draft.id && it.name == "Review" && it.variables.single().key == "baseUrl" })
            }
        }
}
