package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopControlsTest {
    @Test
    fun `compact fields retain their accessible label after entering text`() =
        runComposeUiTest {
            var entered = ""
            setContent {
                var value by remember { mutableStateOf("") }
                AppTheme {
                    CompactTextField(value, {
                        value = it
                        entered = it
                    }, label = "Header name")
                }
            }
            onNodeWithContentDescription("Header name").performTextInput("Content-Type")
            assertEquals("Content-Type", entered)
            onNodeWithContentDescription("Header name").assertExists()
        }

    @Test
    fun `section tabs expose selection and change it on click`() =
        runComposeUiTest {
            setContent {
                var selected by remember { mutableStateOf(0) }
                AppTheme { SectionTabs(listOf("Params", "Headers"), selected, { selected = it }) }
            }
            onNodeWithText("Params").assertIsSelected()
            onNodeWithText("Headers").performClick()
            onNodeWithText("Headers").assertIsSelected()
            onNodeWithText("Params").assertIsNotSelected()
        }

    @Test
    fun `focused splitter responds to keyboard and stays within its bounds`() =
        runComposeUiTest {
            setContent {
                AppTheme {
                    HorizontalSplitPane(
                        modifier = Modifier.size(1000.dp, 400.dp),
                        leftContent = { Box(Modifier) },
                        rightContent = { Box(Modifier) },
                    )
                }
            }
            val splitter = onNodeWithContentDescription("Resize sidebar")
            val before = splitter.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            splitter.performKeyInput {
                pressKey(Key.Tab)
            }
            // Request focus explicitly so this does not depend on the test host's initial traversal position.
            splitter.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            splitter.performKeyInput { pressKey(Key.DirectionRight) }
            val after = splitter.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
            assertTrue(after > before)
            splitter.performKeyInput { pressKey(Key.MoveEnd) }
            val progress = splitter.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(progress.range.endInclusive, progress.current)
        }
}
