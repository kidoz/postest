package su.kidoz.postest.domain.usecase

import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.port.CollectionEncoder
import su.kidoz.postest.domain.port.CollectionFiles

/**
 * Use case for exporting collections to various formats.
 * Currently supports:
 * - Postman Collection v2.1.0
 */
class ExportCollectionUseCase(
    private val postmanExporter: CollectionEncoder,
    private val files: CollectionFiles,
) {
    /**
     * Export a collection to a file in Postman v2.1.0 format.
     *
     * @param collection The collection to export
     * @param path The target file path
     * @return Result indicating success or failure
     */
    suspend fun exportToPostman(
        collection: RequestCollection,
        path: String,
    ): Result<Unit> =
        postmanExporter.encode(collection).fold(
            onSuccess = { files.write(path, it) },
            onFailure = { Result.failure(it) },
        )

    /**
     * Export a collection to JSON string in Postman v2.1.0 format.
     *
     * @param collection The collection to export
     * @return Result containing the JSON string or an error
     */
    fun exportToPostmanString(collection: RequestCollection): Result<String> = postmanExporter.encode(collection)

    /**
     * Get list of supported export formats.
     */
    fun supportedFormats(): List<ExportFormat> =
        listOf(
            ExportFormat(
                name = "Postman Collection",
                extension = "postman_collection.json",
                description = "Postman Collection v2.1.0 format",
            ),
        )
}

data class ExportFormat(
    val name: String,
    val extension: String,
    val description: String,
)
