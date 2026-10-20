package su.kidoz.postest.ui.components.common

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TabBarTest {
    private val tabs =
        listOf(
            TabItem(id = "one", title = "One", method = HttpMethod.GET),
            TabItem(id = "two", title = "Two", method = HttpMethod.POST),
            TabItem(id = "three", title = "Three", method = HttpMethod.PUT),
        )

    private fun ComposeUiTest.setBar(
        onCloseAll: () -> Unit = {},
        onCloseOthers: (String) -> Unit = {},
        onCloseToLeft: (String) -> Unit = {},
        onCloseToRight: (String) -> Unit = {},
    ) {
        setContent {
            AppTheme {
                RequestTabBar(
                    tabs = tabs,
                    selectedTabId = "two",
                    onTabSelect = {},
                    onTabClose = {},
                    onCloseAll = onCloseAll,
                    onCloseOthers = onCloseOthers,
                    onCloseToLeft = onCloseToLeft,
                    onCloseToRight = onCloseToRight,
                )
            }
        }
    }

    private fun ComposeUiTest.openTabMenu(tabId: String) {
        onNodeWithTag("tab-$tabId").performMouseInput {
            moveTo(center)
            press(MouseButton.Secondary)
            release(MouseButton.Secondary)
        }
    }

    @Test
    fun `right click on a tab shows bulk close actions`() =
        runComposeUiTest {
            setBar()
            openTabMenu("two")

            onNodeWithText("Close All").assertExists()
            onNodeWithText("Close Others").assertExists()
            onNodeWithText("Close Tabs to the Left").assertExists()
            onNodeWithText("Close Tabs to the Right").assertExists()
        }

    @Test
    fun `bulk close actions report the right-clicked tab`() =
        runComposeUiTest {
            var closeAll = false
            var others: String? = null
            var left: String? = null
            var right: String? = null
            setBar(
                onCloseAll = { closeAll = true },
                onCloseOthers = { others = it },
                onCloseToLeft = { left = it },
                onCloseToRight = { right = it },
            )

            openTabMenu("two")
            onNodeWithText("Close Others").performClick()
            assertEquals("two", others)

            openTabMenu("two")
            onNodeWithText("Close Tabs to the Left").performClick()
            assertEquals("two", left)

            openTabMenu("two")
            onNodeWithText("Close Tabs to the Right").performClick()
            assertEquals("two", right)

            openTabMenu("two")
            onNodeWithText("Close All").performClick()
            assertTrue(closeAll)
        }

    @Test
    fun `left and right actions are disabled at the ends`() =
        runComposeUiTest {
            setBar()
            openTabMenu("one")

            onNodeWithText("Close Tabs to the Left").assertIsNotEnabled()
            onNodeWithText("Close Tabs to the Right").assertIsEnabled()
        }
}
