package su.kidoz.postest.viewmodel

import su.kidoz.postest.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStoreTest {
    private val tab = TabState(id = "initial")

    private fun store() = WorkspaceStore(WorkspaceState(tabs = listOf(tab), activeTabId = tab.id))

    private val response = HttpResponse(200, "OK", emptyMap(), "ok", "text/plain", 2, ResponseTime(1))

    @Test fun `restore preserves order selection links drafts response errors and dirty state`() {
        val store = store()
        val raw = RequestBody.Raw("raw", "text/custom")
        val json = RequestBody.Json("{}")
        val saved =
            DraftSession(
                tabs =
                    listOf(
                        DraftTab("first", HttpRequest(body = raw), true, listOf(json), "item", response, "previous error"),
                        DraftTab("second", HttpRequest(), false),
                    ),
                activeTabId = "second",
            )
        store.dispatch(WorkspaceCommand.Restore(saved, store.state.value.toDraftSession()))
        val state = store.state.value
        assertEquals(listOf("first", "second"), state.tabs.map { it.id })
        assertEquals("second", state.activeTabId)
        val restored = state.tabs.first()
        assertTrue(restored.isDirty)
        assertEquals(json, restored.bodyDrafts[RequestBody.Json::class])
        assertEquals(raw, restored.request.body)
        assertEquals(response, restored.response)
        assertEquals("previous error", restored.error)
        assertEquals("item", restored.collectionItemId)
        assertFalse(restored.isLoading)
        assertNull(restored.executionId)
        assertTrue(state.isRestored)
    }

    @Test fun `delayed restore merges new tabs and preserves edits and current selection`() {
        val store = store()
        val initial = store.state.value.toDraftSession()
        store.dispatch(WorkspaceCommand.EditRequest(tab.id, tab.request.copy(url = "/typed")))
        store.dispatch(WorkspaceCommand.Restore(DraftSession(tabs = listOfSaved(), activeTabId = "saved"), initial))
        assertEquals(
            listOf("saved", tab.id),
            store.state.value.tabs
                .map { it.id },
        )
        assertEquals(tab.id, store.state.value.activeTabId)
        assertEquals(
            "/typed",
            store.state.value.activeTab
                ?.request
                ?.url,
        )
        // Bootstrap events are idempotent.
        store.dispatch(WorkspaceCommand.Restore(DraftSession(tabs = listOfSaved(), activeTabId = "saved"), initial))
        assertEquals(2, store.state.value.tabs.size)
    }

    @Test fun `current edits win if restored and live tab ids collide`() {
        val store = store()
        val initial = store.state.value.toDraftSession()
        store.dispatch(WorkspaceCommand.EditRequest(tab.id, tab.request.copy(url = "/live")))
        store.dispatch(
            WorkspaceCommand.Restore(
                DraftSession(
                    tabs = listOf(DraftTab(tab.id, HttpRequest(url = "/saved"), false)),
                    activeTabId = tab.id,
                ),
                initial,
            ),
        )
        assertEquals(
            "/live",
            store.state.value.activeTab
                ?.request
                ?.url,
        )
        assertEquals(1, store.state.value.tabs.size)
    }

    @Test fun `late results cannot replace a newer execution or a removed tab`() {
        val store = store()
        store.dispatch(WorkspaceCommand.RequestStarted(tab.id, "old"))
        store.dispatch(WorkspaceCommand.RequestStarted(tab.id, "new"))
        store.dispatch(WorkspaceCommand.RequestFinished(tab.id, "old", response, null))
        assertNull(
            store.state.value.activeTab
                ?.response,
        )
        assertTrue(
            store.state.value.activeTab!!
                .isLoading,
        )
        store.dispatch(WorkspaceCommand.RequestFinished(tab.id, "new", response, null))
        assertEquals(
            response,
            store.state.value.activeTab
                ?.response,
        )
        assertFalse(
            store.state.value.activeTab!!
                .isLoading,
        )
        store.dispatch(WorkspaceCommand.CloseTabs(setOf(tab.id)))
        store.dispatch(WorkspaceCommand.RequestFinished(tab.id, "new", response, null))
        assertEquals(1, store.state.value.tabs.size)
        assertNull(
            store.state.value.activeTab
                ?.response,
        )
    }

    @Test fun `closing freezes edits and clears loading and resuming restores command handling`() {
        val store = store()
        store.dispatch(WorkspaceCommand.RequestStarted(tab.id, "execution"))
        store.dispatch(WorkspaceCommand.BeginClosing)
        store.dispatch(WorkspaceCommand.EditRequest(tab.id, tab.request.copy(url = "/ignored")))
        store.dispatch(WorkspaceCommand.RequestFinished(tab.id, "execution", response, null))
        assertEquals(
            tab.request,
            store.state.value.activeTab
                ?.request,
        )
        assertFalse(
            store.state.value.activeTab!!
                .isLoading,
        )
        assertNull(
            store.state.value.activeTab
                ?.response,
        )
        store.dispatch(WorkspaceCommand.Resume)
        store.dispatch(WorkspaceCommand.EditRequest(tab.id, tab.request.copy(url = "/accepted")))
        assertEquals(
            "/accepted",
            store.state.value.activeTab
                ?.request
                ?.url,
        )
    }

    @Test fun `invalid restored selection falls back to an existing tab`() {
        val store = store()
        store.dispatch(
            WorkspaceCommand.Restore(
                DraftSession(tabs = listOfSaved(), activeTabId = "missing"),
                store.state.value.toDraftSession(),
            ),
        )
        assertEquals("saved", store.state.value.activeTabId)
    }

    private fun listOfSaved(): List<DraftTab> = listOf(DraftTab("saved", HttpRequest(), true))
}
