package su.kidoz.postest.util

import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VariableResolverTest {
    private val resolver = VariableResolver()

    @Test
    fun `resolve should replace variable with environment value`() {
        val environment =
            Environment(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "baseUrl", value = "https://api.example.com"),
                    ),
            )

        val result = resolver.resolve("{{baseUrl}}/users", environment)

        assertEquals("https://api.example.com/users", result)
    }

    @Test
    fun `resolve should handle multiple variables`() {
        val environment =
            Environment(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "baseUrl", value = "https://api.example.com"),
                        Variable(key = "version", value = "v1"),
                    ),
            )

        val result = resolver.resolve("{{baseUrl}}/{{version}}/users", environment)

        assertEquals("https://api.example.com/v1/users", result)
    }

    @Test
    fun `resolve should keep original if variable not found`() {
        val environment =
            Environment(
                name = "Test",
                variables = emptyList(),
            )

        val result = resolver.resolve("{{unknown}}/users", environment)

        assertEquals("{{unknown}}/users", result)
    }

    @Test
    fun `resolve should ignore disabled variables`() {
        val environment =
            Environment(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "baseUrl", value = "https://api.example.com", enabled = false),
                    ),
            )

        val result = resolver.resolve("{{baseUrl}}/users", environment)

        assertEquals("{{baseUrl}}/users", result)
    }

    @Test
    fun `resolve should handle null environment`() {
        val result = resolver.resolve("{{baseUrl}}/users", null)

        assertEquals("{{baseUrl}}/users", result)
    }

    @Test
    fun `resolve should prioritize environment over collection variables`() {
        val environment =
            Environment(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "host", value = "env-host"),
                    ),
            )
        val collectionVariables =
            listOf(
                Variable(key = "host", value = "collection-host"),
            )

        val result = resolver.resolve("{{host}}", environment, collectionVariables)

        assertEquals("env-host", result)
    }

    @Test
    fun `resolve should use collection variables when not in environment`() {
        val environment =
            Environment(
                name = "Test",
                variables = emptyList(),
            )
        val collectionVariables =
            listOf(
                Variable(key = "host", value = "collection-host"),
            )

        val result = resolver.resolve("{{host}}", environment, collectionVariables)

        assertEquals("collection-host", result)
    }

    @Test
    fun `resolve should use global variables as fallback`() {
        val globalVariables =
            listOf(
                Variable(key = "apiKey", value = "global-key"),
            )

        val result = resolver.resolve("{{apiKey}}", null, emptyList(), globalVariables)

        assertEquals("global-key", result)
    }

    @Test
    fun `resolve should handle dynamic uuid variable`() {
        val result = resolver.resolve("{{\$guid}}", null)

        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(result.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `resolve should handle dynamic timestamp variable`() {
        val result = resolver.resolve("{{\$timestamp}}", null)

        // Should be a numeric timestamp
        assertTrue(result.toLongOrNull() != null)
    }

    @Test
    fun `resolve should handle dynamic randomInt variable`() {
        val result = resolver.resolve("{{\$randomInt}}", null)

        val intValue = result.toIntOrNull()
        assertTrue(intValue != null && intValue in 0..999)
    }

    @Test
    fun `resolve should handle dynamic randomEmail variable`() {
        val result = resolver.resolve("{{\$randomEmail}}", null)

        assertTrue(result.endsWith("@example.com"))
    }

    @Test
    fun `resolve should handle whitespace in variable names`() {
        val environment =
            Environment(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "baseUrl", value = "https://api.example.com"),
                    ),
            )

        val result = resolver.resolve("{{ baseUrl }}/users", environment)

        assertEquals("https://api.example.com/users", result)
    }

    @Test
    fun `resolve should not modify string without variables`() {
        val input = "https://api.example.com/users"

        val result = resolver.resolve(input, null)

        assertEquals(input, result)
    }

    @Test
    fun `resolve should generate different values for each uuid call`() {
        val result1 = resolver.resolve("{{\$uuid}}", null)
        val result2 = resolver.resolve("{{\$uuid}}", null)

        assertNotEquals(result1, result2)
    }
}
