package su.kidoz.postest.viewmodel.manager

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.HistoryEntry
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Manager for HTTP request execution.
 * Handles: sending requests, cancellation, job tracking.
 */
class RequestExecutionManager(
    private val executeRequestUseCase: ExecuteRequestUseCase,
    private val tabManager: TabManager,
    private val historyManager: HistoryManager,
    private val sideEffectEmitter: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    // Track active request jobs per tab for cancellation
    private val activeRequestJobs = ConcurrentHashMap<String, Job>()

    /**
     * Sends an HTTP request for the specified tab.
     */
    fun sendRequest(
        tabId: String,
        request: HttpRequest,
    ) {
        tabManager.setTabLoading(tabId, isLoading = true, error = null)

        // Atomically cancel old job and store new one to prevent race conditions
        activeRequestJobs.compute(tabId) { _, oldJob ->
            oldJob?.cancel()

            scope.launch {
                val startTime = System.currentTimeMillis()
                val result = executeRequestUseCase(request)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Remove job from tracking when complete
                activeRequestJobs.remove(tabId)

                result.fold(
                    onSuccess = { response ->
                        // Add to history
                        val historyEntry =
                            HistoryEntry(
                                request = request,
                                response = response,
                                duration = duration,
                            )
                        historyManager.addHistoryEntry(historyEntry)

                        tabManager.setTabResponse(tabId, response = response, error = null)
                    },
                    onFailure = { error ->
                        logger.error(error) { "HTTP request failed: ${error.message}" }

                        val errorMsg = error.message ?: "Unknown error"

                        // Add to history with error details preserved
                        val historyEntry =
                            HistoryEntry(
                                request = request,
                                response = null,
                                errorMessage = errorMsg,
                                duration = duration,
                            )
                        historyManager.addHistoryEntry(historyEntry)

                        tabManager.setTabResponse(tabId, response = null, error = errorMsg)

                        sideEffectEmitter.emit(AppSideEffect.ShowError(errorMsg))
                    },
                )
            }
        }
    }

    /**
     * Cancels the active request for the specified tab.
     */
    fun cancelRequest(tabId: String) {
        activeRequestJobs[tabId]?.let { job ->
            job.cancel()
            activeRequestJobs.remove(tabId)

            tabManager.setTabLoading(tabId, isLoading = false, error = "Request cancelled")

            sideEffectEmitter.emit(AppSideEffect.ShowToast("Request cancelled"))
        }
    }

    /**
     * Cleans up all active request jobs.
     */
    fun cleanup() {
        activeRequestJobs.values.forEach { it.cancel() }
        activeRequestJobs.clear()
    }
}
