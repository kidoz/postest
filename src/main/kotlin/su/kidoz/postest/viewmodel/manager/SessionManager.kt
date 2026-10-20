package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.WorkspaceState
import su.kidoz.postest.viewmodel.toDraftSession

/** Observes recovery snapshots. It cannot mutate live tabs or restore presentation state. */
class SessionManager(
    private val useCase: ManageSessionUseCase,
    private val state: StateFlow<WorkspaceState>,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    private var observer: Job? = null
    private val writer = Mutex()
    private val closing = Mutex()
    private var nextRevision = 0L
    private var committedRevision = -1L
    private var saveErrorShown = false

    /** Loading is coordinated by MainViewModel, which applies one typed restore command. */
    suspend fun load(): DraftSession? =
        try {
            useCase.load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            effects.emit(AppSideEffect.ShowError("Saved drafts could not be restored. A new workspace was opened."))
            null
        }

    @OptIn(FlowPreview::class)
    fun initialize() {
        if (observer?.isActive == true) return
        val baseline = state.value.toDraftSession()
        observer =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                state
                    .map { it.toDraftSession() }
                    .distinctUntilChanged()
                    .dropWhile { it == baseline }
                    .debounce(300)
                    .collect { save(it) }
            }
    }

    /** The caller freezes workspace mutations before this flush. Await writes rather than cancelling their commits. */
    suspend fun prepareToClose(): Boolean =
        closing.withLock {
            observer?.cancelAndJoin()
            val saved = save(state.value.toDraftSession())
            if (!saved) initialize()
            saved
        }

    private suspend fun save(session: DraftSession): Boolean {
        val revision = nextRevision++
        return writer.withLock {
            if (revision <= committedRevision) return@withLock true
            // JDBC writes are synchronous: cancellation cannot safely interrupt a commit already in progress.
            withContext(NonCancellable) {
                try {
                    useCase.save(session)
                    committedRevision = revision
                    saveErrorShown = false
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (!saveErrorShown) {
                        effects.emit(
                            AppSideEffect.ShowError(
                                "Drafts could not be saved. Keep Postest open and retry closing when storage is available.",
                            ),
                        )
                        saveErrorShown = true
                    }
                    false
                }
            }
        }
    }
}
