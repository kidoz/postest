package su.kidoz.postest.viewmodel.manager

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater
import su.kidoz.postest.viewmodel.TabState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RequestExecutionManagerTest {
    private val request = HttpRequest(url = "https://api.example.com/users")
    private val response =
        HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = emptyMap(),
            body = "{}",
            contentType = "application/json",
            size = 2,
            time = ResponseTime(total = 10),
        )
    private val executeRequestUseCase = mockk<ExecuteRequestUseCase>()
    private val historyManager = mockk<HistoryManager>(relaxed = true)
    private val effects = mockk<SideEffectEmitter>(relaxed = true)

    @Test
    fun `history failure does not hide a successful response`() =
        runTest {
            coEvery { executeRequestUseCase(any(), any()) } returns Result.success(response)
            coEvery { historyManager.addHistoryEntry(any()) } throws RuntimeException("storage unavailable")
            var appState = AppState(tabs = listOf(TabState(id = "tab", request = request)), activeTabId = "tab")
            val tabManager = TabManager(StateUpdater { appState = it(appState) }) { appState }
            val manager = RequestExecutionManager(executeRequestUseCase, tabManager, historyManager, effects, this)

            manager.sendRequest("tab", request)
            runCurrent()

            assertEquals(response, appState.activeTab?.response)
            assertNull(appState.activeTab?.error)
            assertFalse(appState.activeTab?.isLoading ?: true)
            verify { effects.emit(AppSideEffect.ShowToast("Request history could not be saved")) }
        }

    @Test
    fun `failures use fixed messages without exception details`() =
        runTest {
            coEvery { executeRequestUseCase(any(), any()) } returns
                Result.failure(java.net.UnknownHostException("secret-host-value"))
            var appState = AppState(tabs = listOf(TabState(id = "tab", request = request)), activeTabId = "tab")
            val tabManager = TabManager(StateUpdater { appState = it(appState) }) { appState }
            val manager = RequestExecutionManager(executeRequestUseCase, tabManager, historyManager, effects, this)

            manager.sendRequest("tab", request)
            runCurrent()

            assertEquals("Host not found", appState.activeTab?.error)
            coVerify { historyManager.addHistoryEntry(match { it.errorMessage == "Host not found" }) }
            verify { effects.emit(AppSideEffect.ShowError("Host not found")) }
        }

    @Test
    fun `an older response never overwrites a newer one`() =
        runTest {
            val firstResponse = CompletableDeferred<Result<HttpResponse>>()
            val newerResponse = response.copy(statusCode = 201, body = "newer")
            var calls = 0
            coEvery { executeRequestUseCase(any(), any()) } coAnswers {
                if (calls++ == 0) {
                    firstResponse.await()
                } else {
                    Result.success(newerResponse)
                }
            }
            var appState =
                AppState(
                    tabs = listOf(TabState(id = "tab", request = HttpRequest(url = "https://api.example.com/first"))),
                    activeTabId = "tab",
                )
            val tabManager = TabManager(StateUpdater { appState = it(appState) }) { appState }
            val manager = RequestExecutionManager(executeRequestUseCase, tabManager, historyManager, effects, this)

            manager.sendRequest("tab", HttpRequest(url = "https://api.example.com/first"))
            runCurrent()
            manager.sendRequest("tab", HttpRequest(url = "https://api.example.com/second"))
            runCurrent()

            assertEquals(newerResponse, appState.activeTab?.response)

            firstResponse.complete(Result.success(response))
            runCurrent()

            assertEquals(newerResponse, appState.activeTab?.response)
            coVerify(exactly = 1) { historyManager.addHistoryEntry(match { it.response == newerResponse }) }
        }
}
