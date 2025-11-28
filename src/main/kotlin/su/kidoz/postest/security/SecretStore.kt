package su.kidoz.postest.security

/**
 * Minimal abstraction for storing small secrets (tokens, passwords).
 */
interface SecretStore {
    val name: String

    fun store(
        key: String,
        value: String,
    ): Result<Unit>

    fun retrieve(key: String): Result<String?>

    fun delete(key: String): Result<Unit>
}
