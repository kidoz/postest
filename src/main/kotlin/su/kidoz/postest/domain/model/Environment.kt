package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Environment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val variables: List<Variable> = emptyList(),
    val isActive: Boolean = false,
)

@Serializable
data class Variable(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val type: VariableType = VariableType.DEFAULT,
    val enabled: Boolean = true,
) {
    val isSecret: Boolean get() = type == VariableType.SECRET
}

@Serializable
enum class VariableType {
    DEFAULT,
    SECRET,
}
