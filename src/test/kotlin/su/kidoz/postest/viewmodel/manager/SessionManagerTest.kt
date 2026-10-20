package su.kidoz.postest.viewmodel.manager

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.*
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.viewmodel.*
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
    private val tab = TabState(id = "initial")
    private val state = MutableStateFlow(WorkspaceState(tabs = listOf(tab), activeTabId = tab.id, isRestored = true))

    private fun edit(url: String) {
        state.value = state.value.copy(tabs = listOf(tab.copy(request = tab.request.copy(url = url), isDirty = true)))
    }

    @Test fun `autosave debounces rapid edits and stores only the latest request`() =
        runTest {
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
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

    @Test fun `closing flushes a last keystroke without waiting for debounce`() =
        runTest {
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
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
        }

    @Test fun `flush awaits an in progress commit then saves the final snapshot`() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val commit = CompletableDeferred<Unit>()
            val committed = mutableListOf<String>()
            coEvery { useCase.save(any()) } coAnswers {
                val url =
                    firstArg<DraftSession>()
                        .tabs
                        .single()
                        .request.url
                if (url == "/old") {
                    started.complete(Unit)
                    commit.await()
                }
                committed.add(url)
            }
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
            edit("/old")
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            assertTrue(started.isCompleted)
            edit("/final")
            val closing = backgroundScope.async { manager.prepareToClose() }
            runCurrent()
            assertFalse(closing.isCompleted)
            commit.complete(Unit)
            runCurrent()
            assertTrue(closing.await())
            assertEquals(listOf("/old", "/final"), committed)
        }

    @Test fun `failed final save reports fixed error and autosave can recover`() =
        runTest {
            coEvery { useCase.save(any()) } throws IllegalStateException("sensitive content")
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
            assertFalse(manager.prepareToClose())
            verify(exactly = 1) {
                effects.emit(
                    AppSideEffect.ShowError("Drafts could not be saved. Keep Postest open and retry closing when storage is available."),
                )
            }
            coEvery { useCase.save(any()) } returns Unit
            edit("/retry")
            runCurrent()
            advanceTimeBy(301)
            runCurrent()
            coVerify {
                useCase.save(
                    match {
                        it.tabs
                            .single()
                            .request.url == "/retry"
                    },
                )
            }
            assertTrue(manager.prepareToClose())
        }

    @Test fun `load failure returns no snapshot and never exposes exception content`() =
        runTest {
            coEvery { useCase.load() } throws IllegalStateException("sensitive content")
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            assertNull(manager.load())
            assertEquals(listOf(tab), state.value.tabs)
            verify { effects.emit(AppSideEffect.ShowError("Saved drafts could not be restored. A new workspace was opened.")) }
        }

    @Test fun `loading and closing flags do not rewrite drafts`() =
        runTest {
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
            state.value = state.value.copy(tabs = listOf(tab.copy(isLoading = true, executionId = "execution")), isClosing = true)
            runCurrent()
            advanceTimeBy(500)
            runCurrent()
            coVerify(exactly = 0) { useCase.save(any()) }
        }

    @Test fun `snapshot preserves request response errors links and inactive drafts while bounding response size`() =
        runTest {
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
            val body = "x".repeat(MAX_PERSISTED_RESPONSE_BYTES + 100)
            val response = HttpResponse(200, "OK", emptyMap(), body, "text/plain", body.length.toLong(), ResponseTime(1))
            val inactive = RequestBody.Json("{}")
            val active = RequestBody.Raw("draft", "text/custom")
            state.value =
                state.value.copy(
                    tabs =
                        listOf(
                            tab.copy(
                                request = tab.request.copy(body = active),
                                bodyDrafts = mapOf(inactive::class to inactive, active::class to active),
                                collectionItemId = "item",
                                response = response,
                                error = "previous error",
                                isDirty = true,
                            ),
                        ),
                )
            assertTrue(manager.prepareToClose())
            coVerify {
                useCase.save(
                    match {
                        val saved = it.tabs.single()
                        saved.bodyDrafts == listOf(inactive) && saved.request.body == active && saved.isDirty &&
                            saved.collectionItemId == "item" && saved.error == "previous error" &&
                            saved.response!!.truncated && saved.response!!.body.length == MAX_PERSISTED_RESPONSE_BYTES
                    },
                )
            }
        }

    @Test fun `closing a tab removes it from the saved workspace`() =
        runTest {
            val second = TabState(id = "second")
            state.value = state.value.copy(tabs = listOf(tab, second))
            val manager = SessionManager(useCase, state, effects, backgroundScope)
            manager.initialize()
            state.value = state.value.copy(tabs = listOf(second), activeTabId = second.id)
            assertTrue(manager.prepareToClose())
            coVerify { useCase.save(match { it.tabs.single().id == "second" && it.activeTabId == "second" }) }
        }
}
