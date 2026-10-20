package su.kidoz.postest.domain.port

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.domain.model.*

interface EnvironmentRepository {
    val snapshot: Flow<EnvironmentSnapshot>
    val environments: Flow<List<Environment>>
    val activeEnvironmentId: Flow<String?>

    suspend fun loadEnvironments(): List<Environment>

    suspend fun createEnvironment(name: String): Environment

    suspend fun saveEnvironment(environment: Environment)

    /** Updates editable content while preserving activation; use setActiveEnvironment to change selection. */
    suspend fun updateEnvironment(environment: Environment)

    suspend fun deleteEnvironment(environmentId: String)

    suspend fun setActiveEnvironment(environmentId: String?)

    suspend fun getActiveEnvironment(): Environment?
}
