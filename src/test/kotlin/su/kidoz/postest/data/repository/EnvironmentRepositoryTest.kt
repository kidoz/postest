package su.kidoz.postest.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnvironmentRepositoryTest {
    private lateinit var repository: EnvironmentRepository

    @BeforeTest
    fun setup() {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        repository = EnvironmentRepository(database)
    }

    @Test
    fun `createEnvironment should create environment and return it`() =
        runTest {
            val environment = repository.createEnvironment("Production")

            assertEquals("Production", environment.name)
            assertNotNull(environment.id)
            assertTrue(environment.variables.isEmpty())
        }

    @Test
    fun `loadEnvironments should return empty list when no environments`() =
        runTest {
            val environments = repository.loadEnvironments()

            assertTrue(environments.isEmpty())
        }

    @Test
    fun `loadEnvironments should return created environments`() =
        runTest {
            repository.createEnvironment("Production")
            repository.createEnvironment("Development")

            val environments = repository.loadEnvironments()

            assertEquals(2, environments.size)
        }

    @Test
    fun `environments flow should emit updates`() =
        runTest {
            repository.createEnvironment("Test")

            val environments = repository.environments.first()

            assertEquals(1, environments.size)
            assertEquals("Test", environments.first().name)
        }

    @Test
    fun `updateEnvironment should update environment name`() =
        runTest {
            val created = repository.createEnvironment("Old Name")
            val updated = created.copy(name = "New Name")

            repository.updateEnvironment(updated)
            val environments = repository.loadEnvironments()

            assertEquals("New Name", environments.first().name)
        }

    @Test
    fun `updateEnvironment should save variables`() =
        runTest {
            val created = repository.createEnvironment("Test")
            val variables =
                listOf(
                    Variable(key = "baseUrl", value = "https://api.example.com"),
                    Variable(key = "apiKey", value = "secret", type = VariableType.SECRET),
                )
            val updated = created.copy(variables = variables)

            repository.updateEnvironment(updated)
            val environments = repository.loadEnvironments()
            val savedEnv = environments.first()

            assertEquals(2, savedEnv.variables.size)
            assertEquals("baseUrl", savedEnv.variables[0].key)
            assertEquals("https://api.example.com", savedEnv.variables[0].value)
            assertTrue(savedEnv.variables[1].isSecret)
        }

    @Test
    fun `deleteEnvironment should remove environment`() =
        runTest {
            val created = repository.createEnvironment("ToDelete")

            repository.deleteEnvironment(created.id)
            val environments = repository.loadEnvironments()

            assertTrue(environments.isEmpty())
        }

    @Test
    fun `setActiveEnvironment should activate environment`() =
        runTest {
            val env1 = repository.createEnvironment("Env1")
            repository.createEnvironment("Env2")

            repository.setActiveEnvironment(env1.id)
            val environments = repository.loadEnvironments()

            val activeEnv = environments.find { it.isActive }
            assertNotNull(activeEnv)
            assertEquals("Env1", activeEnv.name)
        }

    @Test
    fun `setActiveEnvironment should deactivate other environments`() =
        runTest {
            val env1 = repository.createEnvironment("Env1")
            val env2 = repository.createEnvironment("Env2")

            repository.setActiveEnvironment(env1.id)
            repository.setActiveEnvironment(env2.id)
            val environments = repository.loadEnvironments()

            val activeCount = environments.count { it.isActive }
            assertEquals(1, activeCount)
            assertTrue(environments.find { it.id == env2.id }?.isActive == true)
        }

    @Test
    fun `setActiveEnvironment with null should deactivate all`() =
        runTest {
            val env = repository.createEnvironment("Test")
            repository.setActiveEnvironment(env.id)

            repository.setActiveEnvironment(null)
            val environments = repository.loadEnvironments()

            assertTrue(environments.none { it.isActive })
        }

    @Test
    fun `getActiveEnvironment should return active environment`() =
        runTest {
            val env = repository.createEnvironment("Active")
            repository.setActiveEnvironment(env.id)

            val active = repository.getActiveEnvironment()

            assertNotNull(active)
            assertEquals("Active", active.name)
        }

    @Test
    fun `getActiveEnvironment should return null when no active`() =
        runTest {
            repository.createEnvironment("Inactive")

            val active = repository.getActiveEnvironment()

            assertNull(active)
        }

    @Test
    fun `activeEnvironmentId flow should emit updates`() =
        runTest {
            val env = repository.createEnvironment("Test")

            repository.setActiveEnvironment(env.id)
            val activeId = repository.activeEnvironmentId.first()

            assertEquals(env.id, activeId)
        }

    @Test
    fun `variable enabled state should be persisted`() =
        runTest {
            val env = repository.createEnvironment("Test")
            val variables =
                listOf(
                    Variable(key = "enabled", value = "value1", enabled = true),
                    Variable(key = "disabled", value = "value2", enabled = false),
                )
            repository.updateEnvironment(env.copy(variables = variables))

            val loaded = repository.loadEnvironments().first()

            assertTrue(loaded.variables.find { it.key == "enabled" }?.enabled == true)
            assertTrue(loaded.variables.find { it.key == "disabled" }?.enabled == false)
        }
}
