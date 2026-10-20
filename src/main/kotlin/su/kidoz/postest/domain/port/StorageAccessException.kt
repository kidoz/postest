package su.kidoz.postest.domain.port

/** Persistence is inaccessible; consumers must not replace it with an empty workspace. */
open class StorageAccessException(
    message: String,
) : IllegalStateException(message)
