package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.OAuth2Exception
import su.kidoz.postest.domain.model.RequestFailureException
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.WorkspaceCommand
import su.kidoz.postest.viewmodel.WorkspaceStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns request jobs. Live results are accepted only by the matching workspace execution. */
class RequestExecutionManager(
    private val executeRequestUseCase: ExecuteRequestUseCase,
    private val workspace: WorkspaceStore,
    private val historyManager: HistoryManager,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    private data class Execution(
        val id: String,
        val job: Job,
    )

    private val executions = ConcurrentHashMap<String, Execution>()

    fun sendRequest(
        tabId: String,
        request: HttpRequest,
        collectionVariables: List<Variable> = emptyList(),
        context: ExecutionContext? = null,
    ) {
        if (workspace.state.value.isClosing ||
            workspace.state.value.tabs
                .none { it.id == tabId }
        ) {
            return
        }
        val id = UUID.randomUUID().toString()
        workspace.dispatch(WorkspaceCommand.RequestStarted(tabId, id))
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val started = System.currentTimeMillis()
                try {
                    val result =
                        if (context == null) {
                            executeRequestUseCase(request, collectionVariables)
                        } else {
                            executeRequestUseCase.execute(request, context)
                        }
                    coroutineContext.ensureActive()
                    if (executions[tabId]?.id != id) return@launch
                    val error = result.exceptionOrNull()?.let { publicErrorMessage(it) }
                    workspace.dispatch(WorkspaceCommand.RequestFinished(tabId, id, result.getOrNull(), error))
                    error?.let { effects.emit(AppSideEffect.ShowError(it)) }
                    try {
                        historyManager.addHistoryEntry(
                            HistoryEntry(
                                request = request,
                                response = result.getOrNull(),
                                errorMessage = error,
                                duration = System.currentTimeMillis() - started,
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        effects.emit(AppSideEffect.ShowToast("Request history could not be saved"))
                    }
                } catch (cancelled: CancellationException) {
                    workspace.dispatch(WorkspaceCommand.RequestCancelled(tabId, id))
                    throw cancelled
                } catch (_: Exception) {
                    workspace.dispatch(WorkspaceCommand.RequestFinished(tabId, id, null, "Request failed"))
                    effects.emit(AppSideEffect.ShowError("Request failed"))
                } finally {
                    executions.computeIfPresent(tabId) { _, current -> if (current.id == id) null else current }
                }
            }
        executions.put(tabId, Execution(id, job))?.job?.cancel()
        job.start()
    }

    private fun publicErrorMessage(error: Throwable): String =
        when (error) {
            is CancellationException -> throw error
            is RequestFailureException -> error.reason.description
            is OAuth2Exception -> error.failure.description
            else -> "Network error"
        }

    fun cancelRequest(tabId: String) {
        executions.remove(tabId)?.let { execution ->
            workspace.dispatch(WorkspaceCommand.RequestCancelled(tabId, execution.id))
            execution.job.cancel()
            effects.emit(AppSideEffect.ShowToast("Request cancelled"))
        }
    }

    fun cancelMissingTabs(ids: Set<String>) {
        executions.keys.filterNot { it in ids }.forEach { id -> executions.remove(id)?.job?.cancel() }
    }

    suspend fun quiesce() {
        val active = executions.values.toList()
        active.forEach { it.job.cancel() }
        active.forEach { it.job.join() }
        executions.clear()
    }

    fun cleanup() {
        executions.values.forEach { it.job.cancel() }
        executions.clear()
    }
}
