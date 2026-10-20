package su.kidoz.postest.viewmodel.manager

import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.StateUpdater
import su.kidoz.postest.viewmodel.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabManagerTest {
    private var state = AppState()
    private val manager = TabManager(StateUpdater { state = it(state) }) { state }

    @Test
    fun `cancelling close preserves the full modified request`() {
        val id = manager.newTab()
        val request = HttpRequest(url = "https://example.test/orders", body = RequestBody.Json("{\"order\":42}"))
        manager.updateRequest(id, request)
        manager.closeTab(id)
        assertEquals(id, state.pendingCloseTabId)
        assertEquals(request, state.activeTab?.request)
        manager.cancelTabClose()
        assertNull(state.pendingCloseTabId)
        assertEquals(request, state.activeTab?.request)
    }

    @Test
    fun `discard closes only the confirmed tab and selects its neighbor`() {
        val first = TabState(id = "first")
        val edited = TabState(id = "edited", isDirty = true)
        val next = TabState(id = "next")
        state = AppState(tabs = listOf(first, edited, next), activeTabId = edited.id)
        manager.closeTab(edited.id)
        assertEquals(3, state.tabs.size)
        manager.discardPendingTab()
        assertEquals(listOf(first, next), state.tabs)
        assertEquals(next.id, state.activeTabId)
        assertNull(state.pendingCloseTabId)
    }

    @Test
    fun `closing last tab selects the preceding neighbor`() {
        val tabs = listOf(TabState(id = "one"), TabState(id = "two"), TabState(id = "three"))
        state = AppState(tabs = tabs, activeTabId = "three")
        manager.closeTab("three")
        assertEquals("two", state.activeTabId)
    }

    @Test
    fun `discarding the only modified tab leaves a clean workspace`() {
        state = AppState(tabs = listOf(TabState(id = "only", isDirty = true)), activeTabId = "only")
        manager.closeTab("only")
        manager.discardPendingTab()
        assertEquals(1, state.tabs.size)
        assertFalse(state.activeTab!!.isDirty)
        assertEquals(state.tabs.single().id, state.activeTabId)
    }

    @Test
    fun `closing an inactive tab preserves the active selection`() {
        val active = manager.newTab()
        val inactive = state.tabs.first().id
        manager.closeTab(inactive)
        assertEquals(active, state.activeTabId)
        manager.closeTab("missing")
        assertEquals(active, state.activeTabId)
    }

    @Test
    fun `switching formats preserves text form file and content type drafts`() {
        val id = manager.newTab()
        val bodies =
            listOf(
                RequestBody.Json("{\"ok\":true}"),
                RequestBody.Xml("<ok/>"),
                RequestBody.FormUrlEncoded(listOf(KeyValue("name", "draft", enabled = false))),
                RequestBody.FormData(listOf(FormField("upload", "/tmp/example.txt", FormFieldType.FILE))),
                RequestBody.Binary("/tmp/example.bin"),
                RequestBody.Raw("plain draft", "text/custom"),
            )
        bodies.forEach { body -> manager.updateRequest(id, state.activeTab!!.request.copy(body = body)) }
        manager.updateRequest(id, state.activeTab!!.request.copy(body = RequestBody.None))
        bodies.forEach { body ->
            val emptyBody =
                when (body) {
                    is RequestBody.Json -> RequestBody.Json("")
                    is RequestBody.Xml -> RequestBody.Xml("")
                    is RequestBody.FormUrlEncoded -> RequestBody.FormUrlEncoded(emptyList())
                    is RequestBody.FormData -> RequestBody.FormData(emptyList())
                    is RequestBody.Binary -> RequestBody.Binary("")
                    else -> RequestBody.Raw("", "text/plain")
                }
            manager.updateRequest(id, state.activeTab!!.request.copy(body = emptyBody))
            assertEquals(body, state.activeTab?.request?.body)
        }
    }

    @Test
    fun `same format edits replace its draft and survive switching away`() {
        val id = manager.newTab()
        manager.updateRequest(id, state.activeTab!!.request.copy(body = RequestBody.Json("old")))
        manager.updateRequest(id, state.activeTab!!.request.copy(body = RequestBody.Json("updated")))
        manager.updateRequest(id, state.activeTab!!.request.copy(body = RequestBody.None))
        manager.updateRequest(id, state.activeTab!!.request.copy(body = RequestBody.Json("")))
        assertEquals(RequestBody.Json("updated"), state.activeTab?.request?.body)
    }

    @Test
    fun `body drafts stay isolated between requests`() {
        val first = manager.newTab()
        manager.updateRequest(first, state.activeTab!!.request.copy(body = RequestBody.Json("first")))
        val second = manager.newTab()
        manager.updateRequest(second, state.activeTab!!.request.copy(body = RequestBody.Json("second")))
        manager.updateRequest(second, state.activeTab!!.request.copy(body = RequestBody.None))
        manager.selectTab(first)
        manager.updateRequest(first, state.activeTab!!.request.copy(body = RequestBody.None))
        manager.updateRequest(first, state.activeTab!!.request.copy(body = RequestBody.Json("")))
        assertEquals(RequestBody.Json("first"), state.activeTab?.request?.body)
        manager.selectTab(second)
        manager.updateRequest(second, state.activeTab!!.request.copy(body = RequestBody.Json("")))
        assertEquals(RequestBody.Json("second"), state.activeTab?.request?.body)
    }

    @Test
    fun `a no-op update does not mark the request dirty`() {
        val id = manager.newTab()
        manager.updateRequest(id, state.activeTab!!.request)
        assertFalse(state.activeTab!!.isDirty)
        manager.updateRequest(id, state.activeTab!!.request.copy(url = "/changed"))
        assertTrue(state.activeTab!!.isDirty)
    }

    @Test
    fun `openRequest remembers the collection item it was opened from`() {
        val tabId = manager.openRequest(HttpRequest(url = "https://example.test/item"), collectionItemId = "item-1")
        assertEquals("item-1", state.activeTab?.collectionItemId)
        manager.updateRequest(tabId, state.activeTab!!.request.copy(url = "https://example.test/edited"))
        assertEquals("item-1", state.activeTab?.collectionItemId)
    }

    @Test
    fun `new tabs are not linked to a collection item`() {
        manager.newTab()
        assertNull(state.activeTab?.collectionItemId)
    }
}
