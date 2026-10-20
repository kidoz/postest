package su.kidoz.postest.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.EnvironmentSnapshot
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.OAuth2Failure
import su.kidoz.postest.domain.model.OAuth2Token
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestFailureException
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.usecase.AuthorizeOAuth2UseCase
import su.kidoz.postest.domain.usecase.ConnectWebSocketUseCase
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
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
    private lateinit var connectWebSocketUseCase: ConnectWebSocketUseCase
    private lateinit var authorizeOAuth2UseCase: AuthorizeOAuth2UseCase
    private lateinit var manageCollectionsUseCase: ManageCollectionsUseCase
    private lateinit var manageEnvironmentsUseCase: ManageEnvironmentsUseCase
    private lateinit var manageHistoryUseCase: ManageHistoryUseCase
    private lateinit var importCollectionUseCase: ImportCollectionUseCase
    private lateinit var exportCollectionUseCase: ExportCollectionUseCase
    private lateinit var manageSessionUseCase: ManageSessionUseCase

    private val collectionsFlow = MutableStateFlow<List<RequestCollection>>(emptyList())
    private val environmentsFlow = MutableStateFlow<List<Environment>>(emptyList())
    private val activeEnvironmentIdFlow = MutableStateFlow<String?>(null)
    private val historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        executeRequestUseCase = mockk()
        connectWebSocketUseCase = mockk()
        authorizeOAuth2UseCase = mockk()
        manageCollectionsUseCase =
            mockk {
                every { collections } returns collectionsFlow
                coEvery { loadCollections() } returns emptyList()
            }
        manageEnvironmentsUseCase =
            mockk {
                every { snapshot } returns
                    combine(environmentsFlow, activeEnvironmentIdFlow) { environments, selected ->
                        EnvironmentSnapshot(environments.map { it.copy(isActive = it.id == selected) })
                    }
                coEvery { getActiveEnvironment() } answers {
                    environmentsFlow.value.find { it.id == activeEnvironmentIdFlow.value }
                }
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
        manageSessionUseCase =
            mockk {
                coEvery { load() } returns null
                coEvery { save(any()) } returns Unit
            }
    }

    @Test
    fun `inaccessible drafts block startup and closing never overwrites them`() =
        runTest(testDispatcher) {
            coEvery { manageSessionUseCase.load() } throws
                su.kidoz.postest.domain.port
                    .StorageAccessException("Unavailable")
            val viewModel =
                MainViewModel(
                    executeRequestUseCase,
                    connectWebSocketUseCase,
                    authorizeOAuth2UseCase,
                    manageCollectionsUseCase,
                    manageEnvironmentsUseCase,
                    manageHistoryUseCase,
                    importCollectionUseCase,
                    exportCollectionUseCase,
                    manageSessionUseCase,
                )
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.state.value.isInitialized)
            assertNotNull(viewModel.state.value.initializationError)
            assertTrue(viewModel.prepareToClose())
            coVerify(exactly = 0) { manageSessionUseCase.save(any()) }
            viewModel.cleanup()
        }

    @Test
    fun `authentication failures from repository observations block autosave`() =
        runTest(testDispatcher) {
            every { manageCollectionsUseCase.collections } returns
                kotlinx.coroutines.flow.flow {
                    throw su.kidoz.postest.domain.port
                        .StorageAccessException("Unavailable")
                }
            val viewModel =
                MainViewModel(
                    executeRequestUseCase,
                    connectWebSocketUseCase,
                    authorizeOAuth2UseCase,
                    manageCollectionsUseCase,
                    manageEnvironmentsUseCase,
                    manageHistoryUseCase,
                    importCollectionUseCase,
                    exportCollectionUseCase,
                    manageSessionUseCase,
                )
            testScheduler.advanceUntilIdle()
            assertFalse(viewModel.state.value.isInitialized)
            assertNotNull(viewModel.state.value.initializationError)
            viewModel.updateRequest(HttpRequest(url = "https://example.test"))
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.prepareToClose())
            coVerify(exactly = 0) { manageSessionUseCase.save(any()) }
            viewModel.cleanup()
        }

    @Test
    fun `WebSocket tabs connect through the WebSocket use case`() =
        runTest(testDispatcher) {
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns
                Result.failure(
                    su.kidoz.postest.domain.model.RequestFailureException(
                        su.kidoz.postest.domain.model.RequestFailure.HOST_NOT_FOUND,
                    ),
                )
            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()

            viewModel.updateRequest(
                HttpRequest(
                    protocol = su.kidoz.postest.domain.model.RequestProtocol.WEBSOCKET,
                    url = "wss://example.test/socket",
                ),
            )
            viewModel.connectWebSocket()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { connectWebSocketUseCase.connect(any(), any()) }
        }

    @Test
    fun `OAuth2 authorization stores the token on the active tab`() =
        runTest(testDispatcher) {
            coEvery { authorizeOAuth2UseCase.authorize(any(), any()) } returns
                Result.success(
                    su.kidoz.postest.domain.model
                        .OAuth2Token(accessToken = "token-1", expiresAt = 123),
                )
            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            viewModel.updateRequest(
                HttpRequest(
                    url = "https://api.example.test",
                    auth =
                        su.kidoz.postest.domain.model.AuthConfig.OAuth2(
                            tokenUrl = "https://provider.test/token",
                            clientId = "client",
                        ),
                ),
            )

            viewModel.authorizeOAuth2()
            testScheduler.advanceUntilIdle()

            val auth =
                viewModel.state.value.activeTab
                    ?.request
                    ?.auth as su.kidoz.postest.domain.model.AuthConfig.OAuth2
            assertEquals("token-1", auth.accessToken)
            assertEquals(123, auth.expiresAt)
        }

    @Test
    fun `expired OAuth2 tokens are refreshed and persisted before sending`() =
        runTest(testDispatcher) {
            coEvery { authorizeOAuth2UseCase.token(any(), any()) } returns
                Result.success(OAuth2Token(accessToken = "new-token", refreshToken = "rotated", expiresAt = 456))
            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.success(createSuccessResponse())
            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            viewModel.updateRequest(
                HttpRequest(
                    url = "https://api.example.test",
                    auth =
                        AuthConfig.OAuth2(
                            tokenUrl = "https://provider.test/token",
                            clientId = "client",
                            accessToken = "old",
                            refreshToken = "refresh-1",
                            expiresAt = 1,
                        ),
                ),
            )

            viewModel.sendRequest()
            testScheduler.advanceUntilIdle()

            val auth =
                viewModel.state.value.activeTab
                    ?.request
                    ?.auth as AuthConfig.OAuth2
            assertEquals("new-token", auth.accessToken)
            assertEquals("rotated", auth.refreshToken)
            assertEquals(456, auth.expiresAt)
            coVerify(exactly = 1) {
                executeRequestUseCase.execute(match { (it.auth as AuthConfig.OAuth2).accessToken == "new-token" }, any())
            }
        }

    @Test
    fun `failed OAuth2 refresh blocks sending`() =
        runTest(testDispatcher) {
            coEvery { authorizeOAuth2UseCase.token(any(), any()) } returns
                Result.failure(OAuth2Exception(OAuth2Failure.TOKEN_REQUEST_FAILED))
            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            viewModel.updateRequest(
                HttpRequest(
                    url = "https://api.example.test",
                    auth = AuthConfig.OAuth2(tokenUrl = "https://provider.test/token", refreshToken = "refresh-1"),
                ),
            )

            viewModel.sendRequest()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 0) { executeRequestUseCase.execute(any(), any()) }
        }

    @Test
    fun `websocket connection uses the refreshed OAuth2 token`() =
        runTest(testDispatcher) {
            coEvery { authorizeOAuth2UseCase.token(any(), any()) } returns
                Result.success(OAuth2Token(accessToken = "ws-token", refreshToken = "rotated"))
            coEvery { connectWebSocketUseCase.connect(any(), any()) } returns
                Result.failure(RequestFailureException(RequestFailure.HOST_NOT_FOUND))
            val viewModel = createViewModel()
            testScheduler.advanceUntilIdle()
            viewModel.updateRequest(
                HttpRequest(
                    protocol = su.kidoz.postest.domain.model.RequestProtocol.WEBSOCKET,
                    url = "wss://api.example.test/socket",
                    auth = AuthConfig.OAuth2(tokenUrl = "https://provider.test/token", refreshToken = "refresh-1"),
                ),
            )

            viewModel.connectWebSocket()
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) {
                connectWebSocketUseCase.connect(match { (it.auth as AuthConfig.OAuth2).accessToken == "ws-token" }, any())
            }
        }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel =
        MainViewModel(
            executeRequestUseCase,
            connectWebSocketUseCase,
            authorizeOAuth2UseCase,
            manageCollectionsUseCase,
            manageEnvironmentsUseCase,
            manageHistoryUseCase,
            importCollectionUseCase,
            exportCollectionUseCase,
            manageSessionUseCase,
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
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()
            val tabToClose =
                viewModel.state.value.tabs
                    .first()
                    .id

            viewModel.closeTab(tabToClose)
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()
            val firstTabId =
                viewModel.state.value.tabs
                    .first()
                    .id

            viewModel.selectTab(firstTabId)
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()

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

            coEvery { executeRequestUseCase.execute(any(), any()) } coAnswers {
                // Delay to observe loading state
                kotlinx.coroutines.delay(100)
                Result.success(createSuccessResponse())
            }

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()

            val loadingState = viewModel.state.value.activeTab
            assertTrue(loadingState?.isLoading == true)
        }

    @Test
    fun `sendRequest should update response on success`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val response = createSuccessResponse()
            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.success(response)

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
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

            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.failure(RuntimeException("Network error"))

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
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

            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.success(createSuccessResponse())

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageHistoryUseCase.addHistoryEntry(any()) }
        }

    @Test
    fun `sendRequest should add to history on failure`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.failure(RuntimeException("Error"))

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageHistoryUseCase.addHistoryEntry(any()) }
        }

    @Test
    fun `sendRequest cancellation clears loading without writing history`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { executeRequestUseCase.execute(any(), any()) } throws CancellationException("Request cancelled")

            viewModel.updateRequest(HttpRequest(url = "https://api.example.com"))
            testDispatcher.scheduler.runCurrent()
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { manageHistoryUseCase.addHistoryEntry(any()) }
            val activeTab = viewModel.state.value.activeTab
            assertNotNull(activeTab)
            assertEquals("Request cancelled", activeTab.error)
            assertNull(activeTab.response)
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
            testDispatcher.scheduler.runCurrent()

            val state = viewModel.state.value
            assertEquals(2, state.tabs.size)
            assertEquals(request.url, state.activeTab?.request?.url)
        }

    @Test
    fun `openRequest should remember the collection item id`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.openRequest(
                HttpRequest(url = "https://api.example.com/users"),
                collectionItemId = "item-1",
            )
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                "item-1",
                viewModel.state.value.activeTab
                    ?.collectionItemId,
            )
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
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()

            assertEquals(!initialTheme, viewModel.state.value.isDarkTheme)
        }

    @Test
    fun `showEnvironmentDialog should set flag`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEnvironmentDialog()
            testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.state.value.showEnvironmentDialog)
        }

    @Test
    fun `hideEnvironmentDialog should clear flag`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showEnvironmentDialog()
            testDispatcher.scheduler.runCurrent()
            viewModel.hideEnvironmentDialog()
            testDispatcher.scheduler.runCurrent()

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
            testDispatcher.scheduler.runCurrent()
            viewModel.createCollection("Test Collection")
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { manageCollectionsUseCase.createCollection("Test Collection") }
            assertFalse(viewModel.state.value.showNewCollectionDialog)
        }

    @Test
    fun `folder and move operations should call use case`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery {
                manageCollectionsUseCase.addFolderToCollection(any(), any(), any(), any())
            } returns CollectionItem.Folder(name = "Folder")
            coEvery { manageCollectionsUseCase.moveCollectionItem(any(), any()) } returns true

            viewModel.addFolderToCollection("collection-1", "Folder", "folder-1")
            testDispatcher.scheduler.runCurrent()
            viewModel.moveCollectionItem("item-1", "folder-2")
            testDispatcher.scheduler.runCurrent()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                manageCollectionsUseCase.addFolderToCollection("collection-1", "Folder", "", "folder-1")
            }
            coVerify { manageCollectionsUseCase.moveCollectionItem("item-1", "folder-2") }
        }

    @Test
    fun `selectEnvironment should call use case`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            coEvery { manageEnvironmentsUseCase.setActiveEnvironment(any()) } returns Unit

            viewModel.selectEnvironment("env-1")
            testDispatcher.scheduler.runCurrent()
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
            testDispatcher.scheduler.runCurrent()
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

            assertEquals(env.copy(isActive = true), viewModel.state.value.activeEnvironment)
        }

    @Test
    fun `startup restores saved work while preserving edits accepted before startup was scheduled`() =
        runTest {
            val loaded = kotlinx.coroutines.CompletableDeferred<su.kidoz.postest.domain.model.DraftSession?>()
            coEvery { manageSessionUseCase.load() } coAnswers { loaded.await() }
            val viewModel = createViewModel()
            viewModel.updateRequest(HttpRequest(url = "/typed-before-startup"))
            testDispatcher.scheduler.runCurrent()
            assertFalse(viewModel.state.value.isInitialized)
            loaded.complete(
                su.kidoz.postest.domain.model.DraftSession(
                    tabs =
                        listOf(
                            su.kidoz.postest.domain.model
                                .DraftTab("saved", HttpRequest(url = "/saved"), true),
                        ),
                    activeTabId = "saved",
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value.isInitialized)
            assertEquals(
                listOf("/saved", "/typed-before-startup"),
                viewModel.state.value.tabs
                    .map { it.request.url },
            )
            assertEquals(
                "/typed-before-startup",
                viewModel.state.value.activeTab
                    ?.request
                    ?.url,
            )
            viewModel.cleanup()
        }

    @Test
    fun `close waits for startup and persists restored work`() =
        runTest {
            val loaded = kotlinx.coroutines.CompletableDeferred<su.kidoz.postest.domain.model.DraftSession?>()
            coEvery { manageSessionUseCase.load() } coAnswers { loaded.await() }
            val viewModel = createViewModel()
            val closing = kotlinx.coroutines.CoroutineScope(testDispatcher).async { viewModel.prepareToClose() }
            testDispatcher.scheduler.runCurrent()
            assertFalse(closing.isCompleted)
            loaded.complete(
                su.kidoz.postest.domain.model.DraftSession(
                    tabs =
                        listOf(
                            su.kidoz.postest.domain.model
                                .DraftTab("saved", HttpRequest(), true),
                        ),
                    activeTabId = "saved",
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(closing.await())
            coVerify { manageSessionUseCase.save(match { it.tabs.single().id == "saved" }) }
            viewModel.cleanup()
        }

    @Test
    fun `sending after selection waits for its commit and uses the captured environment`() =
        runTest {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val env = Environment(id = "selected", name = "Selected", isActive = true)
            coEvery { manageEnvironmentsUseCase.setActiveEnvironment(env.id) } coAnswers {
                gate.await()
                activeEnvironmentIdFlow.value = env.id
            }
            environmentsFlow.value = listOf(env)
            coEvery { executeRequestUseCase.execute(any(), any()) } returns Result.success(createSuccessResponse())
            viewModel.selectEnvironment(env.id)
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 0) { executeRequestUseCase.execute(any(), any()) }
            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { executeRequestUseCase.execute(any(), match { it.environment == env }) }
            viewModel.cleanup()
        }

    @Test
    fun `failed observation is modeled explicitly and does not kill workspace editing`() =
        runTest {
            every { manageCollectionsUseCase.collections } returns kotlinx.coroutines.flow.flow { error("sensitive data") }
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Collections could not be loaded", viewModel.state.value.initializationError)
            viewModel.newTab()
            testDispatcher.scheduler.runCurrent()
            assertEquals(2, viewModel.state.value.tabs.size)
            viewModel.sendRequest()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 0) { executeRequestUseCase.execute(any(), any()) }
            viewModel.cleanup()
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
