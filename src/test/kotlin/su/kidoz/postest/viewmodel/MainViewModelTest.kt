package su.kidoz.postest.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var executeRequestUseCase: ExecuteRequestUseCase
    private lateinit var manageCollectionsUseCase: ManageCollectionsUseCase
    private lateinit var manageEnvironmentsUseCase: ManageEnvironmentsUseCase
    private lateinit var manageHistoryUseCase: ManageHistoryUseCase
    private lateinit var importCollectionUseCase: ImportCollectionUseCase
    private lateinit var exportCollectionUseCase: ExportCollectionUseCase

    private val collectionsFlow = MutableStateFlow<List<RequestCollection>>(emptyList())
    private val environmentsFlow = MutableStateFlow<List<Environment>>(emptyList())
    private val activeEnvironmentIdFlow = MutableStateFlow<String?>(null)
    private val historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        executeRequestUseCase = mockk()
        manageCollectionsUseCase =
            mockk {
                every { collections } returns collectionsFlow
                coEvery { loadCollections() } returns emptyList()
            }
        manageEnvironmentsUseCase =
            mockk {
                every { environments } returns environmentsFlow
                every { activeEnvironmentId } returns activeEnvironmentIdFlow
                coEvery { loadEnvironments() } returns emptyList()
            }
        manageHistoryUseCase =
            mockk {
                every { history } returns historyFlow
                coEvery { loadHistory() } returns emptyList()
                coEvery { addHistoryEntry(any()) } returns Unit
            }
        importCollectionUseCase = mockk()
        exportCollectionUseCase = mockk()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel =
        MainViewModel(
            executeRequestUseCase,
            manageCollectionsUseCase,
            manageEnvironmentsUseCase,
            manageHistoryUseCase,
            importCollectionUseCase,
            exportCollectionUseCase,
        )

    @Test
    fun `initial state should have one tab`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.state.value

            assertEquals(1, state.tabs.size)
            assertNotNull(state.activeTabId)
            assertEquals(state.tabs.first().id, state.activeTabId)
        }

    @Test
    fun `newTab should add new tab and select it`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val initialTabCount = viewModel.state.value.tabs.size
            viewModel.newTab()

            val state = viewModel.state.value
            assertEquals(initialTabCount + 1, state.tabs.size)
            assertEquals(state.tabs.last().id, state.activeTabId)
        }

    @Test
    fun `closeTab should remove tab`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.newTab()
            val tabToClose =
                viewModel.state.value.tabs
                    .first()
                    .id

            viewModel.closeTab(tabToClose)

            val state = viewModel.state.value
            assertEquals(1, state.tabs.size)
            assertFalse(state.tabs.any { it.id == tabToClose })
        }

    @Test
    fun `closeTab should always keep at least one tab`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val onlyTab =
                viewModel.state.value.tabs
                    .first()
            viewModel.closeTab(onlyTab.id)

            val state = viewModel.state.value
            assertEquals(1, state.tabs.size)
            assertNotNull(state.activeTabId)
        }

    @Test
    fun `selectTab should change active tab`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.newTab()
            val firstTabId =
                viewModel.state.value.tabs
                    .first()
                    .id

            viewModel.selectTab(firstTabId)

            assertEquals(firstTabId, viewModel.state.value.activeTabId)
        }

    @Test
    fun `updateRequest should modify active tab request`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val newRequest =
                HttpRequest(
                    method = HttpMethod.POST,
                    url = "https://api.example.com/users",
                )
            viewModel.updateRequest(newRequest)

            val activeTab = viewModel.state.value.activeTab
            assertNotNull(activeTab)
            assertEquals(HttpMethod.POST, activeTab.request.method)
            assertEquals("https://api.example.com/users", activeTab.request.url)
            assertTrue(activeTab.isDirty)
        }

    @Test
    fun `sendRequest should set loading state`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.invoke(any()) } coAnswers {
                // Delay to observe loading state
                kotlinx.coroutines.delay(100)
                Result.success(createSuccessResponse())
            }

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            viewModel.sendRequest()

            val loadingState = viewModel.state.value.activeTab
            assertTrue(loadingState?.isLoading == true)
        }

    @Test
    fun `sendRequest should update response on success`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val response = createSuccessResponse()
            coEvery { executeRequestUseCase.invoke(any()) } returns Result.success(response)

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            viewModel.sendRequest()
            testDispatcher.scheduler.advanceUntilIdle()

            val activeTab = viewModel.state.value.activeTab
            assertNotNull(activeTab)
            assertFalse(activeTab.isLoading)
            assertNotNull(activeTab.response)
            assertEquals(200, activeTab.response?.statusCode)
        }

    @Test
    fun `sendRequest should set error on failure`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.invoke(any()) } returns Result.failure(RuntimeException("Network error"))

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            viewModel.sendRequest()
            testDispatcher.scheduler.advanceUntilIdle()

            val activeTab = viewModel.state.value.activeTab
            assertNotNull(activeTab)
            assertFalse(activeTab.isLoading)
            assertNull(activeTab.response)
            assertEquals("Network error", activeTab.error)
        }

    @Test
    fun `sendRequest should add to history on success`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.invoke(any()) } returns Result.success(createSuccessResponse())

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            viewModel.sendRequest()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageHistoryUseCase.addHistoryEntry(any()) }
        }

    @Test
    fun `sendRequest should add to history on failure`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.invoke(any()) } returns Result.failure(RuntimeException("Error"))

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            viewModel.sendRequest()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageHistoryUseCase.addHistoryEntry(any()) }
        }

    @Test
    fun `openRequest should create new tab with request`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )
            viewModel.openRequest(request)

            val state = viewModel.state.value
            assertEquals(2, state.tabs.size)
            assertEquals(request.url, state.activeTab?.request?.url)
        }

    @Test
    fun `openHistoryEntry should create tab with request and response`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val entry =
                HistoryEntry(
                    request = HttpRequest(url = "https://api.example.com"),
                    response = createSuccessResponse(),
                    duration = 100,
                )
            viewModel.openHistoryEntry(entry)

            val state = viewModel.state.value
            val activeTab = state.activeTab
            assertNotNull(activeTab)
            assertEquals(entry.request.url, activeTab.request.url)
            assertNotNull(activeTab.response)
        }

    @Test
    fun `toggleTheme should toggle dark theme`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val initialTheme = viewModel.state.value.isDarkTheme
            viewModel.toggleTheme()

            assertEquals(!initialTheme, viewModel.state.value.isDarkTheme)
        }

    @Test
    fun `showEnvironmentDialog should set flag`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEnvironmentDialog()

            assertTrue(viewModel.state.value.showEnvironmentDialog)
        }

    @Test
    fun `hideEnvironmentDialog should clear flag`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEnvironmentDialog()
            viewModel.hideEnvironmentDialog()

            assertFalse(viewModel.state.value.showEnvironmentDialog)
        }

    @Test
    fun `createCollection should call use case and hide dialog`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { manageCollectionsUseCase.createCollection(any(), any()) } returns
                RequestCollection(
                    id = "1",
                    name = "Test",
                )

            viewModel.showNewCollectionDialog()
            viewModel.createCollection("Test Collection")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageCollectionsUseCase.createCollection("Test Collection") }
            assertFalse(viewModel.state.value.showNewCollectionDialog)
        }

    @Test
    fun `selectEnvironment should call use case`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { manageEnvironmentsUseCase.setActiveEnvironment(any()) } returns Unit

            viewModel.selectEnvironment("env-1")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageEnvironmentsUseCase.setActiveEnvironment("env-1") }
        }

    @Test
    fun `clearHistory should call use case`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { manageHistoryUseCase.clearHistory() } returns Unit

            viewModel.clearHistory()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageHistoryUseCase.clearHistory() }
        }

    @Test
    fun `state should update when collections flow emits`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val collections = listOf(RequestCollection(id = "1", name = "API"))
            collectionsFlow.value = collections
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(collections, viewModel.state.value.collections)
        }

    @Test
    fun `state should update when environments flow emits`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val environments = listOf(Environment(id = "1", name = "Production"))
            environmentsFlow.value = environments
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(environments, viewModel.state.value.environments)
        }

    @Test
    fun `activeEnvironment should return correct environment`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val env = Environment(id = "env-1", name = "Prod")
            environmentsFlow.value = listOf(env)
            activeEnvironmentIdFlow.value = "env-1"
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(env, viewModel.state.value.activeEnvironment)
        }

    private fun createSuccessResponse() =
        HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = emptyMap(),
            body = """{"success": true}""",
            contentType = "application/json",
            size = 17,
            time = ResponseTime(total = 100),
        )
}
