package su.kidoz.postest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.postest.data.security.StorageMode
import su.kidoz.postest.ui.theme.AppTheme
import su.kidoz.postest.viewmodel.StorageState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class StorageScreenTest {
    @Test
    fun `storage choice and confirmation support keyboard activation`() =
        runComposeUiTest {
            var selected: StorageMode? = null
            setContent {
                AppTheme {
                    Box(Modifier.size(620.dp, 720.dp)) {
                        StorageScreen(StorageState.ChooseMode, {}, {}, {}, onChooseMode = { selected = it })
                    }
                }
            }
            onNodeWithText("Unencrypted").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Unencrypted").performKeyInput { pressKey(Key.Spacebar) }
            onNodeWithText("Create workspace").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            onNodeWithText("Create workspace").performKeyInput { pressKey(Key.Enter) }
            runOnIdle { assertEquals(StorageMode.UNENCRYPTED, selected) }
        }

    @Test
    fun `first run explains both choices and waits for confirmation in both themes`() {
        listOf(false, true).forEach { dark ->
            runComposeUiTest {
                var selected: StorageMode? = null
                setContent {
                    AppTheme(darkTheme = dark) {
                        Box(Modifier.size(620.dp, 720.dp)) {
                            StorageScreen(StorageState.ChooseMode, {}, {}, {}, onChooseMode = { selected = it })
                        }
                    }
                }
                onNodeWithText("Encrypted (recommended)").assertIsDisplayed()
                onNodeWithText("Unencrypted").assertIsDisplayed()
                runOnIdle { assertNull(selected) }
                val screenshot = File("build/reports/storage-ui/${if (dark) "dark" else "light"}.png")
                screenshot.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", screenshot)
                onNodeWithText("Create workspace").performClick()
                runOnIdle { assertEquals(StorageMode.ENCRYPTED, selected) }
                onNodeWithText("Unencrypted").performClick()
                onNodeWithText("Create workspace").performClick()
                runOnIdle { assertEquals(StorageMode.UNENCRYPTED, selected) }
            }
        }
    }

    @Test
    fun `unencrypted choice and confirmation remain accessible in a narrow window`() =
        runComposeUiTest {
            var selected: StorageMode? = null
            setContent {
                AppTheme {
                    Box(Modifier.size(380.dp, 500.dp)) {
                        StorageScreen(StorageState.ChooseMode, {}, {}, {}, onChooseMode = { selected = it })
                    }
                }
            }
            onNodeWithText("Unencrypted").performScrollTo().performClick()
            onNodeWithText("Create workspace").performScrollTo().assertIsDisplayed().performClick()
            runOnIdle { assertEquals(StorageMode.UNENCRYPTED, selected) }
        }
}
