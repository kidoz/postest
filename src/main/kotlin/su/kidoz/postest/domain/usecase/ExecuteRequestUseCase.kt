package su.kidoz.postest.domain.usecase

import su.kidoz.postest.data.http.RequestExecutor
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse

class ExecuteRequestUseCase(
    private val requestExecutor: RequestExecutor,
    private val environmentRepository: EnvironmentRepository,
) {
    suspend operator fun invoke(request: HttpRequest): Result<HttpResponse> {
        val activeEnvironment = environmentRepository.getActiveEnvironment()
        return requestExecutor.execute(request, activeEnvironment)
    }
}
