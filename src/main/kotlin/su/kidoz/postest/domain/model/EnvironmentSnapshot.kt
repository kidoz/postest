package su.kidoz.postest.domain.model

/** Environments and selection are read and published as one coherent snapshot. */
data class EnvironmentSnapshot(
    val environments: List<Environment>,
) {
    val activeEnvironment: Environment? get() = environments.firstOrNull { it.isActive }
    val activeEnvironmentId: String? get() = activeEnvironment?.id
}
