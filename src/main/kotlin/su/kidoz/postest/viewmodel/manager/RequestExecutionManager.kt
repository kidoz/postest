package su.kidoz.postest.viewmodel.manager

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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
        collectionVariables: List<su.kidoz.postest.domain.model.Variable> = emptyList(),
    ) {
        tabManager.setTabLoading(tabId, isLoading = true, error = null)

        // Atomically cancel old job and store new one to prevent race conditions
        activeRequestJobs.compute(tabId) { _, oldJob ->
            oldJob?.cancel()

            scope.launch {
                val currentJob = coroutineContext.job
                val startTime = System.currentTimeMillis()
                try {
                    val result = executeRequestUseCase(request, collectionVariables)
                    val duration = System.currentTimeMillis() - startTime

                    // Skip writing results if this job was cancelled or superseded by a newer request
                    currentJob.ensureActive()
                    if (activeRequestJobs[tabId] != currentJob) return@launch

                    result.fold(
                        onSuccess = { response ->
                            // Publish the network result before any storage work so history
                            // failures cannot hide a successful response.
                            tabManager.setTabResponse(tabId, response = response, error = null)
                            addHistory(
                                HistoryEntry(
                                    request = request,
                                    response = response,
                                    duration = duration,
                                ),
                            )
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error

                            val errorMsg = publicErrorMessage(error)
                            logger.error { "HTTP request failed: ${error::class.simpleName}" }

                            tabManager.setTabResponse(tabId, response = null, error = errorMsg)
                            sideEffectEmitter.emit(AppSideEffect.ShowError(errorMsg))
                            addHistory(
                                HistoryEntry(
                                    request = request,
                                    response = null,
                                    errorMessage = errorMsg,
                                    duration = duration,
                                ),
                            )
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (activeRequestJobs[tabId] == currentJob) {
                        logger.error { "Request execution failed: ${error::class.simpleName}" }
                        tabManager.setTabResponse(tabId, response = null, error = "Request failed")
                        sideEffectEmitter.emit(AppSideEffect.ShowError("Request failed"))
                    }
                } finally {
                    // Keep ownership until all work is finished so an older job can never
                    // remove or overwrite a newer one.
                    if (activeRequestJobs[tabId] == currentJob) {
                        activeRequestJobs.remove(tabId, currentJob)
                    }
                }
            }
        }
    }

    private suspend fun addHistory(entry: HistoryEntry) {
        try {
            historyManager.addHistoryEntry(entry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Request history could not be saved"))
        }
    }

    private fun publicErrorMessage(error: Throwable): String =
        when (error) {
            is SecurityException -> error.message ?: "Request blocked"
            is IllegalArgumentException -> error.message?.takeIf { it.isNotBlank() } ?: "Invalid request"
            is java.net.UnknownHostException -> "Host not found"
            is java.net.ConnectException -> "Connection failed"
            is java.net.SocketTimeoutException -> "Request timed out"
            is io.ktor.client.plugins.HttpRequestTimeoutException -> "Request timed out"
            is javax.net.ssl.SSLException -> "TLS error"
            is java.io.IOException -> "Network error"
            else -> "Network error"
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
