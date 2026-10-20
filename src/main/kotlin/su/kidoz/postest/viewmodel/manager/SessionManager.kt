package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.model.DraftTab
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater
import su.kidoz.postest.viewmodel.TabState

/** Restores once, autosaves request edits, and flushes before an orderly exit. */
class SessionManager(
    private val useCase: ManageSessionUseCase,
    private val state: StateFlow<AppState>,
    private val updater: StateUpdater<AppState>,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    private var startup: Job? = null
    private var observer: Job? = null
    private var saveErrorShown = false

    fun initialize() {
        val initial = snapshot(state.value)
        startup =
            scope.launch {
                try {
                    useCase.load()?.let { session -> restore(session, initial) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Never include serialization errors: they can contain request secrets.
                    effects.emit(AppSideEffect.ShowError("Saved drafts could not be restored. A new workspace was opened."))
                }
                observe()
            }
    }

    @OptIn(FlowPreview::class)
    private fun observe() {
        observer =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                state
                    .map(::snapshot)
                    .distinctUntilChanged()
                    .drop(1)
                    .debounce(300)
                    .collect { save(it) }
            }
    }

    private fun restore(
        session: DraftSession,
        initial: DraftSession,
    ) {
        val restored =
            session.tabs.map { tab ->
                TabState(
                    id = tab.id,
                    request = tab.request,
                    collectionItemId = tab.collectionItemId,
                    isDirty = tab.isDirty,
                    bodyDrafts = tab.bodyDrafts.associateBy { it::class },
                )
            }
        updater.update { current ->
            val unchanged = snapshot(current) == initial
            val tabs = if (unchanged) restored else (restored + current.tabs).distinctBy { it.id }
            val requestedActive = if (unchanged) session.activeTabId else current.activeTabId
            current.copy(tabs = tabs, activeTabId = requestedActive?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id)
        }
    }

    /** Called before disposing the UI so even edits inside the debounce window survive. */
    suspend fun prepareToClose(): Boolean {
        startup?.join()
        observer?.cancelAndJoin()
        val saved = save(snapshot(state.value))
        if (!saved) observe()
        return saved
    }

    private suspend fun save(session: DraftSession): Boolean =
        try {
            useCase.save(session)
            saveErrorShown = false
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!saveErrorShown) {
                effects.emit(
                    AppSideEffect.ShowError("Drafts could not be saved. Keep Postest open and retry closing when storage is available."),
                )
                saveErrorShown = true
            }
            false
        }

    private fun snapshot(app: AppState): DraftSession =
        DraftSession(
            tabs =
                app.tabs.map { tab ->
                    val activeType = (tab.request.body ?: RequestBody.None)::class
                    val inactiveDrafts = tab.bodyDrafts.values.filter { it::class != activeType && it !is RequestBody.None }
                    DraftTab(
                        id = tab.id,
                        request = tab.request,
                        isDirty = tab.isDirty,
                        bodyDrafts = inactiveDrafts,
                        collectionItemId = tab.collectionItemId,
                    )
                },
            activeTabId = app.activeTabId,
        )
}
