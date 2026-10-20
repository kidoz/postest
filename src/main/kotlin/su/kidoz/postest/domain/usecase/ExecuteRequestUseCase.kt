package su.kidoz.postest.domain.usecase

import su.kidoz.postest.domain.model.*
import su.kidoz.postest.domain.port.EnvironmentRepository
import su.kidoz.postest.domain.port.RequestTransport

class ExecuteRequestUseCase(
    private val requestExecutor: RequestTransport,
    private val environmentRepository: EnvironmentRepository,
) {
    suspend operator fun invoke(
        request: HttpRequest,
        collectionVariables: List<Variable> = emptyList(),
    ): Result<HttpResponse> = execute(request, ExecutionContext(environmentRepository.getActiveEnvironment(), collectionVariables))

    suspend fun execute(
        request: HttpRequest,
        context: ExecutionContext,
    ): Result<HttpResponse> =
        when (val outcome = requestExecutor.executeRequest(request, context)) {
            is RequestOutcome.Success -> Result.success(outcome.response)
            is RequestOutcome.Failure -> Result.failure(RequestFailureException(outcome.reason))
        }
}
