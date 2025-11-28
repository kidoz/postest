package su.kidoz.postest.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import java.util.UUID

private val logger = KotlinLogging.logger {}

class EnvironmentRepository(
    private val database: PostestDatabase,
) {
    private val _environments = MutableStateFlow<List<Environment>>(emptyList())
    val environments: Flow<List<Environment>> = _environments.asStateFlow()

    private val _activeEnvironmentId = MutableStateFlow<String?>(null)
    val activeEnvironmentId: Flow<String?> = _activeEnvironmentId.asStateFlow()

    /**
     * Initial load of environments from database.
     * Called once at startup.
     */
    suspend fun loadEnvironments() =
        withContext(Dispatchers.IO) {
            val dbEnvironments = database.postestQueries.selectAllEnvironments().executeAsList()
            val result =
                dbEnvironments.map { dbEnv ->
                    val variables =
                        database.postestQueries
                            .selectVariablesByEnvironmentId(dbEnv.id)
                            .executeAsList()
                            .map { dbVar ->
                                Variable(
                                    id = dbVar.id,
                                    key = dbVar.key,
                                    value = dbVar.value_,
                                    type = if (dbVar.is_secret == 1L) VariableType.SECRET else VariableType.DEFAULT,
                                    enabled = dbVar.enabled == 1L,
                                )
                            }

                    Environment(
                        id = dbEnv.id,
                        name = dbEnv.name,
                        variables = variables,
                        isActive = dbEnv.is_active == 1L,
                    )
                }

            _environments.value = result
            _activeEnvironmentId.value = result.find { it.isActive }?.id
            result
        }

    /**
     * Creates a new environment.
     * Uses incremental update - appends to existing list instead of full reload.
     */
    suspend fun createEnvironment(name: String): Environment =
        withContext(Dispatchers.IO) {
            val environment =
                Environment(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    variables = emptyList(),
                )

            database.postestQueries.insertEnvironment(
                id = environment.id,
                name = environment.name,
                is_active = 0,
                created_at = System.currentTimeMillis(),
                updated_at = System.currentTimeMillis(),
            )

            // Incremental update: append new environment
            _environments.update { currentList ->
                currentList + environment
            }

            environment
        }

    /**
     * Saves a new environment with variables.
     * Uses incremental update - appends to existing list instead of full reload.
     */
    suspend fun saveEnvironment(environment: Environment) =
        withContext(Dispatchers.IO) {
            database.postestQueries.transaction {
                if (environment.isActive) {
                    database.postestQueries.deactivateAllEnvironments()
                }

                database.postestQueries.insertEnvironment(
                    id = environment.id,
                    name = environment.name,
                    is_active = if (environment.isActive) 1 else 0,
                    created_at = System.currentTimeMillis(),
                    updated_at = System.currentTimeMillis(),
                )

                environment.variables.forEach { variable ->
                    database.postestQueries.insertEnvironmentVariable(
                        id = variable.id.ifBlank { UUID.randomUUID().toString() },
                        environment_id = environment.id,
                        key = variable.key,
                        value_ = variable.value,
                        is_secret = if (variable.isSecret) 1 else 0,
                        enabled = if (variable.enabled) 1 else 0,
                    )
                }
            }

            // Incremental update: append new environment and update active state
            _environments.update { currentList ->
                val updatedList =
                    if (environment.isActive) {
                        // Deactivate all other environments in memory
                        currentList.map { it.copy(isActive = false) }
                    } else {
                        currentList
                    }
                updatedList + environment
            }

            if (environment.isActive) {
                _activeEnvironmentId.value = environment.id
            }
        }

    /**
     * Updates an existing environment.
     * Uses incremental update - replaces the specific environment instead of full reload.
     */
    suspend fun updateEnvironment(environment: Environment) =
        withContext(Dispatchers.IO) {
            database.postestQueries.transaction {
                if (environment.isActive) {
                    database.postestQueries.deactivateAllEnvironments()
                }

                database.postestQueries.updateEnvironment(
                    id = environment.id,
                    name = environment.name,
                    is_active = if (environment.isActive) 1 else 0,
                    updated_at = System.currentTimeMillis(),
                )

                database.postestQueries.deleteVariablesByEnvironmentId(environment.id)
                environment.variables.forEach { variable ->
                    database.postestQueries.insertEnvironmentVariable(
                        id = variable.id.ifBlank { UUID.randomUUID().toString() },
                        environment_id = environment.id,
                        key = variable.key,
                        value_ = variable.value,
                        is_secret = if (variable.isSecret) 1 else 0,
                        enabled = if (variable.enabled) 1 else 0,
                    )
                }
            }

            // Incremental update: replace the updated environment
            _environments.update { currentList ->
                currentList.map { env ->
                    when {
                        env.id == environment.id -> environment
                        environment.isActive -> env.copy(isActive = false)
                        else -> env
                    }
                }
            }

            if (environment.isActive) {
                _activeEnvironmentId.value = environment.id
            }
        }

    /**
     * Deletes an environment.
     * Uses incremental update - filters out the environment instead of full reload.
     */
    suspend fun deleteEnvironment(environmentId: String) =
        withContext(Dispatchers.IO) {
            // If deleting the active environment, deactivate it first
            val wasActive = _activeEnvironmentId.value == environmentId
            if (wasActive) {
                database.postestQueries.deactivateAllEnvironments()
                logger.info { "Deactivated environment before deletion: $environmentId" }
            }

            database.postestQueries.deleteEnvironment(environmentId)

            // Incremental update: remove the deleted environment
            _environments.update { currentList ->
                currentList.filter { it.id != environmentId }
            }

            if (wasActive) {
                _activeEnvironmentId.value = null
            }
        }

    /**
     * Sets the active environment.
     * Uses incremental update - updates isActive flags in memory instead of full reload.
     */
    suspend fun setActiveEnvironment(environmentId: String?) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deactivateAllEnvironments()
            if (environmentId != null) {
                database.postestQueries.activateEnvironment(environmentId)
            }

            // Incremental update: update isActive flags in memory
            _environments.update { currentList ->
                currentList.map { env ->
                    env.copy(isActive = env.id == environmentId)
                }
            }

            _activeEnvironmentId.value = environmentId
        }

    suspend fun getActiveEnvironment(): Environment? =
        withContext(Dispatchers.IO) {
            val activeId = _activeEnvironmentId.value ?: return@withContext null
            _environments.value.find { it.id == activeId }
        }
}
