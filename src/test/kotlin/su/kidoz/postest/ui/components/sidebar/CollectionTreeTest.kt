package su.kidoz.postest.ui.components.sidebar

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CollectionTreeTest {
    private fun ComposeUiTest.setTree(
        collection: RequestCollection,
        selectedCollectionItemId: String? = null,
        onCreateFolder: (collectionId: String, parentFolderId: String?, name: String) -> Unit = { _, _, _ -> },
        onRemoveFromFolder: (CollectionItem) -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        setContent {
            AppTheme {
                CollectionTree(
                    collections = listOf(collection),
                    onRequestClick = {},
                    selectedCollectionItemId = selectedCollectionItemId,
                    onNewCollection = {},
                    onNewRequest = {},
                    onCreateFolder = onCreateFolder,
                    onImportCollection = {},
                    onExportCollection = {},
                    onRenameCollection = { _, _ -> },
                    onDeleteCollection = {},
                    onRenameItem = { _, _ -> },
                    onDeleteItem = {},
                    onRemoveFromFolder = onRemoveFromFolder,
                    modifier = modifier,
                )
            }
        }
    }

    private fun request(
        id: String,
        name: String = id,
    ) = CollectionItem.Request(id = id, name = name, request = HttpRequest(url = "https://api.example.com/$id"))

    @Test
    fun `selection uses the collection item id instead of the shared request id`() =
        runComposeUiTest {
            val sharedRequest = HttpRequest(url = "https://api.example.com/users")
            val first = CollectionItem.Request(id = "item-1", name = "First", request = sharedRequest)
            val second = CollectionItem.Request(id = "item-2", name = "Second", request = sharedRequest)
            setTree(
                collection = RequestCollection(name = "Collection", items = listOf(first, second)),
                selectedCollectionItemId = second.id,
            )

            onNodeWithTag("collection-item-${first.id}").assertIsNotSelected()
            onNodeWithTag("collection-item-${second.id}").assertIsSelected()
        }

    @Test
    fun `collection menu offers creating a folder`() =
        runComposeUiTest {
            setTree(RequestCollection(name = "Collection"))

            onNodeWithContentDescription("Collection menu").performClick()
            onNodeWithText("Add Folder").assertExists()
        }

    @Test
    fun `folder menu offers creating a nested folder`() =
        runComposeUiTest {
            val folder = CollectionItem.Folder(id = "folder-1", name = "Folder")
            setTree(RequestCollection(name = "Collection", items = listOf(folder)))

            onNodeWithContentDescription("Folder menu").performClick()
            onNodeWithText("Add Folder").assertExists()
        }

    @Test
    fun `request inside a folder offers removing it from the folder`() =
        runComposeUiTest {
            val item = request("item-1")
            val folder = CollectionItem.Folder(id = "folder-1", name = "Folder", items = listOf(item))
            setTree(RequestCollection(name = "Collection", items = listOf(folder)))

            onNodeWithContentDescription("Request menu").performClick()
            onNodeWithText("Remove from Folder").assertExists()
        }

    @Test
    fun `request at the collection root has no remove from folder action`() =
        runComposeUiTest {
            setTree(RequestCollection(name = "Collection", items = listOf(request("item-1"))))

            onNodeWithContentDescription("Request menu").performClick()
            onNodeWithText("Remove from Folder").assertDoesNotExist()
        }

    @Test
    fun `dragging a nested request onto the collection header removes it from the folder`() =
        runComposeUiTest {
            val item = request("item-1")
            val folder = CollectionItem.Folder(id = "folder-1", name = "Folder", items = listOf(item))
            var removed: CollectionItem? = null
            setTree(
                collection = RequestCollection(name = "Collection", items = listOf(folder)),
                onRemoveFromFolder = { removed = it },
            )

            val itemBounds = onNodeWithTag("collection-item-${item.id}").fetchSemanticsNode().boundsInRoot
            val headerBounds = onNodeWithText("Collection").fetchSemanticsNode().boundsInRoot

            onRoot().performMouseInput {
                moveTo(itemBounds.center)
                press()
                moveTo(itemBounds.center - Offset(0f, 20f))
                moveTo(headerBounds.center)
                moveTo(headerBounds.center)
                release()
            }

            assertEquals(item.id, removed?.id)
        }

    @Test
    fun `dragging near the top scrolls an offscreen collection header into reach`() =
        runComposeUiTest {
            val item = request("nested")
            val folder = CollectionItem.Folder(id = "folder", name = "Folder", items = listOf(item))
            var removed: CollectionItem? = null
            setTree(
                collection = RequestCollection(name = "Collection", items = (1..25).map { request("root-$it") } + folder),
                onRemoveFromFolder = { removed = it },
                modifier = Modifier.size(360.dp, 260.dp),
            )

            onNodeWithTag("collection-item-${item.id}").performScrollTo().assertIsDisplayed()
            onNodeWithText("Collection").assertIsNotDisplayed()
            val itemBounds = onNodeWithTag("collection-item-${item.id}").fetchSemanticsNode().boundsInRoot
            val viewport = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            onRoot().performMouseInput {
                moveTo(itemBounds.center)
                press()
                moveTo(itemBounds.center - Offset(0f, 20f))
                moveTo(Offset(viewport.center.x, viewport.top + 10f))
            }

            mainClock.advanceTimeBy(3000)
            onNodeWithText("Collection").assertIsDisplayed()
            val headerBounds = onNodeWithText("Collection").fetchSemanticsNode().boundsInRoot
            onRoot().performMouseInput {
                moveTo(headerBounds.center)
                release()
            }
            assertEquals(item.id, removed?.id)
        }

    @Test
    fun `edge scrolling pauses away from the edge and stops when the drag ends`() =
        runComposeUiTest {
            val item = request("nested")
            val folder = CollectionItem.Folder(id = "folder", name = "Folder", items = listOf(item))
            var removed: CollectionItem? = null
            setTree(
                collection = RequestCollection(name = "Collection", items = (1..25).map { request("root-$it") } + folder),
                onRemoveFromFolder = { removed = it },
                modifier = Modifier.size(360.dp, 260.dp),
            )
            onNodeWithTag("collection-item-${item.id}").performScrollTo()
            val itemBounds = onNodeWithTag("collection-item-${item.id}").fetchSemanticsNode().boundsInRoot
            val viewport = onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot

            fun scrollPosition(): Float {
                val axis = onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                return axis.value()
            }

            mainClock.autoAdvance = false
            onRoot().performMouseInput {
                moveTo(itemBounds.center)
                press()
                moveTo(itemBounds.center - Offset(0f, 20f))
                moveTo(viewport.center)
            }
            mainClock.advanceTimeByFrame()
            val initialPosition = scrollPosition()
            mainClock.advanceTimeBy(1000)
            assertEquals(initialPosition, scrollPosition())

            onRoot().performMouseInput { moveTo(Offset(viewport.center.x, viewport.top + 10f)) }
            mainClock.advanceTimeBy(160)
            assertTrue(scrollPosition() < initialPosition)
            onRoot().performMouseInput { release() }
            mainClock.advanceTimeByFrame()
            val positionAfterRelease = scrollPosition()
            mainClock.advanceTimeBy(3000)
            assertEquals(positionAfterRelease, scrollPosition())
            assertNull(removed)
        }

    @Test
    fun `remove from folder reports the selected item`() =
        runComposeUiTest {
            val item = request("item-1")
            val folder = CollectionItem.Folder(id = "folder-1", name = "Folder", items = listOf(item))
            var removed: CollectionItem? = null
            setTree(
                collection = RequestCollection(name = "Collection", items = listOf(folder)),
                onRemoveFromFolder = { removed = it },
            )

            onNodeWithContentDescription("Request menu").performClick()
            onNodeWithText("Remove from Folder").performClick()

            assertEquals(item.id, removed?.id)
        }
}
