package su.kidoz.postest.domain.port

/** File system access is provided by a platform adapter, not performed by use cases. */
interface CollectionFiles {
    suspend fun read(
        path: String,
        maxBytes: Int,
    ): Result<String>

    suspend fun write(
        path: String,
        content: String,
    ): Result<Unit>
}
