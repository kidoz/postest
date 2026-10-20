package su.kidoz.postest.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.EnvironmentSnapshot
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import java.util.UUID
import su.kidoz.postest.domain.port.EnvironmentRepository as EnvironmentRepositoryPort

class EnvironmentRepository(
    private val database: PostestDatabase,
) : EnvironmentRepositoryPort {
    private val queries = database.postestQueries
    override val snapshot: Flow<EnvironmentSnapshot> = queries.observeEnvironmentChanges().asFlow().map { readSnapshot() }
    override val environments: Flow<List<Environment>> = snapshot.map { it.environments }
    override val activeEnvironmentId: Flow<String?> = snapshot.map { it.activeEnvironmentId }

    private suspend fun readSnapshot(): EnvironmentSnapshot =
        withContext(Dispatchers.IO) {
            database.transactionWithResult {
                val variables = queries.selectAllEnvironmentVariables().executeAsList().groupBy { it.environment_id }
                EnvironmentSnapshot(
                    queries.selectAllEnvironments().executeAsList().map { row ->
                        Environment(
                            id = row.id,
                            name = row.name,
                            isActive = row.is_active == 1L,
                            variables =
                                variables[row.id].orEmpty().map { variable ->
                                    Variable(
                                        id = variable.id,
                                        key = variable.key,
                                        value = variable.value_,
                                        type = if (variable.is_secret == 1L) VariableType.SECRET else VariableType.DEFAULT,
                                        enabled = variable.enabled == 1L,
                                    )
                                },
                        )
                    },
                )
            }
        }

    override suspend fun loadEnvironments(): List<Environment> = readSnapshot().environments

    override suspend fun getActiveEnvironment(): Environment? = readSnapshot().activeEnvironment

    override suspend fun createEnvironment(name: String): Environment {
        val environment = Environment(name = name)
        saveEnvironment(environment)
        return environment
    }

    override suspend fun saveEnvironment(environment: Environment) {
        withContext(Dispatchers.IO) {
            database.transaction {
                if (environment.isActive) queries.deactivateAllEnvironments()
                queries.insertEnvironment(
                    id = environment.id,
                    name = environment.name,
                    is_active = if (environment.isActive) 1 else 0,
                    created_at = System.currentTimeMillis(),
                    updated_at = System.currentTimeMillis(),
                )
                insertVariables(environment)
            }
        }
    }

    override suspend fun updateEnvironment(environment: Environment) {
        withContext(Dispatchers.IO) {
            database.transaction {
                if (environment.isActive) queries.deactivateAllEnvironments()
                queries.updateEnvironment(
                    id = environment.id,
                    name = environment.name,
                    is_active = if (environment.isActive) 1 else 0,
                    updated_at = System.currentTimeMillis(),
                )
                queries.deleteVariablesByEnvironmentId(environment.id)
                insertVariables(environment)
            }
        }
    }

    override suspend fun deleteEnvironment(environmentId: String) {
        withContext(Dispatchers.IO) {
            database.transaction {
                queries.deleteVariablesByEnvironmentId(environmentId)
                queries.deleteEnvironment(environmentId)
            }
        }
    }

    override suspend fun setActiveEnvironment(environmentId: String?) {
        withContext(Dispatchers.IO) {
            database.transaction {
                require(environmentId == null || queries.selectEnvironmentById(environmentId).executeAsOneOrNull() != null) {
                    "Environment does not exist"
                }
                queries.deactivateAllEnvironments()
                environmentId?.let { queries.activateEnvironment(it) }
            }
        }
    }

    private fun insertVariables(environment: Environment) {
        environment.variables.forEach { variable ->
            queries.insertEnvironmentVariable(
                id = variable.id.ifBlank { UUID.randomUUID().toString() },
                environment_id = environment.id,
                key = variable.key,
                value_ = variable.value,
                is_secret = if (variable.isSecret) 1 else 0,
                enabled = if (variable.enabled) 1 else 0,
            )
        }
    }
}
