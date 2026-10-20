package su.kidoz.postest.data.security

import su.kidoz.postest.domain.port.StorageAccessException

enum class StorageProblem(
    val description: String,
) {
    KEY_STORE_UNAVAILABLE("The OS credential store is locked or unavailable. Unlock it and retry."),
    KEY_MISSING("The database key is missing from the OS credential store. Enter your saved recovery key."),
    INVALID_DATA("The database could not be validated. Check your database backup and, for encrypted storage, your recovery key."),
    ALREADY_OPEN("Another Postest instance is using this storage. Close it and retry."),
    MIGRATION_FAILED("Storage could not be prepared. Your existing database has been retained."),
    LEGACY_CLEANUP_FAILED(
        "Encrypted storage is ready, but the old plaintext database could not be removed. Close other database tools and retry.",
    ),
}

/** Fixed messages only: native, SQL and crypto errors can contain application content. */
class StorageException(
    val problem: StorageProblem,
) : StorageAccessException(problem.description)

/** Implementations must distinguish absence from locked/unavailable storage and never use a file fallback. */
interface NativeKeyStore {
    fun read(vaultId: String): ByteArray?

    /** Called under the application's exclusive storage lock; replacement requires verified recovery material. */
    fun write(
        vaultId: String,
        key: ByteArray,
    )
}
