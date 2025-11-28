package su.kidoz.postest.util

import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.Variable
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class VariableResolver {
    private val variablePattern = Regex("\\{\\{([^}]+)\\}\\}")

    fun resolve(
        input: String,
        environment: Environment?,
        collectionVariables: List<Variable> = emptyList(),
        globalVariables: List<Variable> = emptyList(),
    ): String =
        variablePattern.replace(input) { match ->
            val varName = match.groupValues[1].trim()
            findVariable(varName, environment, collectionVariables, globalVariables)
                ?: match.value // Keep original if not found
        }

    private fun findVariable(
        name: String,
        environment: Environment?,
        collectionVariables: List<Variable>,
        globalVariables: List<Variable>,
    ): String? {
        // Priority: Environment > Collection > Global > Dynamic
        return environment?.variables?.find { it.key == name && it.enabled }?.value
            ?: collectionVariables.find { it.key == name && it.enabled }?.value
            ?: globalVariables.find { it.key == name && it.enabled }?.value
            ?: resolveDynamicVariable(name)
    }

    private fun resolveDynamicVariable(name: String): String? =
        when (name) {
            "\$guid", "\$uuid", "\$randomUUID" -> UUID.randomUUID().toString()
            "\$timestamp" -> System.currentTimeMillis().toString()
            "\$isoTimestamp" -> Instant.now().toString()
            "\$randomInt" -> Random.nextInt(0, 1000).toString()
            "\$randomString" -> generateRandomString(10)
            "\$randomEmail" -> "${generateRandomString(8)}@example.com"
            else -> null
        }

    private fun generateRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}
