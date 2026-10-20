package su.kidoz.postest.domain.port

import su.kidoz.postest.domain.model.ExecutionContext
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestOutcome

interface RequestTransport {
    /** Expected transport errors are values; coroutine cancellation always propagates. */
    suspend fun executeRequest(
        request: HttpRequest,
        context: ExecutionContext,
    ): RequestOutcome
}
