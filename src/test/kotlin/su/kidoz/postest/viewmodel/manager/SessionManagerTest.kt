package su.kidoz.postest.viewmodel.manager

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater
import su.kidoz.postest.viewmodel.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val useCase =
        mockk<ManageSessionUseCase> {
            coEvery { load() } returns null
            coEvery { save(any()) } returns Unit
        }
    private val effects = mockk<SideEffectEmitter>(relaxed = true)
    private val initial = TabState(id = "initial")
    private val state = MutableStateFlow(AppState(tabs = listOf(initial), activeTabId = initial.id))

    private fun manager(scope: CoroutineScope) =
        SessionManager(
            useCase,
            state,
            StateUpdater { state.value = it(state.value) },
            effects,
            scope,
        )

    private fun edit(url: String) {
        state.value = state.value.copy(tabs = listOf(initial.copy(request = initial.request.copy(url = url), isDirty = true)))
    }

    @Test
    fun `restores tab order selection dirty state and inactive body drafts`() =
        runTest {
            val json = RequestBody.Json("{\"draft\":true}")
            val raw = RequestBody.Raw("raw draft", "text/custom")
            val tabs =
                listOf(
                    DraftTab("first", HttpRequest(name = "First", body = raw), true, listOf(json, raw)),
                    DraftTab("second", HttpRequest(name = "Second"), false),
                )
            coEvery { useCase.load() } returns DraftSession(tabs = tabs, activeTabId = "second")
            manager(backgroundScope).initialize()
            runCurrent()
            assertEquals(listOf("first", "second"), state.value.tabs.map { it.id })
            assertEquals("second", state.value.activeTabId)
            assertTrue(
                state.value.tabs
                    .first()
                    .isDirty,
            )
            assertEquals(
                json,
                state.value.tabs
                    .first()
                    .bodyDrafts[RequestBody.Json::class],
            )
            assertFalse(
                state.value.tabs
                    .first()
                    .isLoading,
            )
            assertNull(
                state.value.tabs
                    .first()
                    .response,
            )
            assertNull(
                state.value.tabs
                    .first()
                    .error,
            )
            coVerify(exactly = 0) { useCase.save(any()) }
        }

    @Test
    fun `autosave debounces rapid edits and stores only the latest request`() =
        runTest {
            manager(backgroundScope).initialize()
            runCurrent()
            edit("/first")
            runCurrent()
            advanceTimeBy(100)
            edit("/latest")
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            coVerify(exactly = 1) {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/latest"
                    },
                )
            }
            coVerify(exactly = 1) { useCase.save(any()) }
        }

    @Test
    fun `closing flushes an edit still inside the debounce interval`() =
        runTest {
            val manager = manager(backgroundScope)
            manager.initialize()
            runCurrent()
            edit("/last-keystroke")
            assertTrue(manager.prepareToClose())
            advanceTimeBy(1000)
            runCurrent()
            coVerify(exactly = 1) {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/last-keystroke"
                    },
                )
            }
            coVerify(exactly = 1) { useCase.save(any()) }
        }

    @Test
    fun `close cancels an older autosave before writing the final snapshot`() =
        runTest {
            val saving = CompletableDeferred<Unit>()
            coEvery {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/old"
                    },
                )
            } coAnswers {
                saving.complete(Unit)
                awaitCancellation()
            }
            val manager = manager(backgroundScope)
            manager.initialize()
            runCurrent()
            edit("/old")
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            assertTrue(saving.isCompleted)
            edit("/final")
            assertTrue(manager.prepareToClose())
            coVerify(exactly = 1) {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/final"
                    },
                )
            }
        }

    @Test
    fun `startup merges restored drafts with edits made while storage was loading`() =
        runTest {
            val loaded = CompletableDeferred<DraftSession?>()
            coEvery { useCase.load() } coAnswers { loaded.await() }
            manager(backgroundScope).initialize()
            runCurrent()
            edit("/typed-during-startup")
            loaded.complete(DraftSession(tabs = listOf(DraftTab("saved", HttpRequest(name = "Saved"), true)), activeTabId = "saved"))
            runCurrent()
            assertEquals(listOf("saved", "initial"), state.value.tabs.map { it.id })
            assertEquals("initial", state.value.activeTabId)
            assertEquals(
                "/typed-during-startup",
                state.value.activeTab
                    ?.request
                    ?.url,
            )
        }

    @Test
    fun `a failed final save keeps the app open and autosave can recover`() =
        runTest {
            coEvery { useCase.save(any()) } throws IllegalStateException("storage unavailable")
            val manager = manager(backgroundScope)
            manager.initialize()
            runCurrent()
            assertFalse(manager.prepareToClose())
            verify(exactly = 1) { effects.emit(ofType<AppSideEffect.ShowError>()) }
            coEvery { useCase.save(any()) } returns Unit
            edit("/after-retry")
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            assertTrue(manager.prepareToClose())
            coVerify {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/after-retry"
                    },
                )
            }
        }

    @Test
    fun `restore failures keep a usable workspace and never expose exception content`() =
        runTest {
            coEvery { useCase.load() } throws IllegalStateException("sensitive request content")
            manager(backgroundScope).initialize()
            runCurrent()
            assertEquals(listOf(initial), state.value.tabs)
            verify(exactly = 1) {
                effects.emit(AppSideEffect.ShowError("Saved drafts could not be restored. A new workspace was opened."))
            }
        }

    @Test
    fun `response and loading changes do not rewrite request drafts`() =
        runTest {
            manager(backgroundScope).initialize()
            runCurrent()
            state.value = state.value.copy(tabs = listOf(initial.copy(isLoading = true, error = "temporary error")))
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            coVerify(exactly = 0) { useCase.save(any()) }
        }

    @Test
    fun `closing a draft removes it from the next saved workspace`() =
        runTest {
            val second = TabState(id = "second")
            state.value = state.value.copy(tabs = listOf(initial, second))
            val manager = manager(backgroundScope)
            manager.initialize()
            runCurrent()
            state.value = state.value.copy(tabs = listOf(second), activeTabId = second.id)
            assertTrue(manager.prepareToClose())
            coVerify { useCase.save(match { it.tabs.map { tab -> tab.id } == listOf("second") && it.activeTabId == "second" }) }
        }

    @Test
    fun `closing during startup waits for saved drafts before flushing`() =
        runTest {
            val loaded = CompletableDeferred<DraftSession?>()
            coEvery { useCase.load() } coAnswers { loaded.await() }
            val manager = manager(backgroundScope)
            manager.initialize()
            val closing = backgroundScope.async { manager.prepareToClose() }
            runCurrent()
            assertFalse(closing.isCompleted)
            loaded.complete(DraftSession(tabs = listOf(DraftTab("saved", HttpRequest(), true)), activeTabId = "saved"))
            runCurrent()
            assertTrue(closing.await())
            coVerify(exactly = 1) { useCase.save(match { it.tabs.single().id == "saved" }) }
        }
}
