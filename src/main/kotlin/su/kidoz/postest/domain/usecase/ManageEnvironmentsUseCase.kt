package su.kidoz.postest.domain.usecase

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.domain.model.Environment

class ManageEnvironmentsUseCase(
    private val environmentRepository: EnvironmentRepository,
) {
    val environments: Flow<List<Environment>> = environmentRepository.environments
    val activeEnvironmentId: Flow<String?> = environmentRepository.activeEnvironmentId

    suspend fun loadEnvironments(): List<Environment> = environmentRepository.loadEnvironments()

    suspend fun createEnvironment(name: String): Environment = environmentRepository.createEnvironment(name)

    suspend fun saveEnvironment(environment: Environment) {
        environmentRepository.saveEnvironment(environment)
    }

    suspend fun updateEnvironment(environment: Environment) {
        environmentRepository.updateEnvironment(environment)
    }

    suspend fun deleteEnvironment(environmentId: String) {
        environmentRepository.deleteEnvironment(environmentId)
    }

    suspend fun setActiveEnvironment(environmentId: String?) {
        environmentRepository.setActiveEnvironment(environmentId)
    }

    suspend fun getActiveEnvironment(): Environment? = environmentRepository.getActiveEnvironment()
}
