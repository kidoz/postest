package su.kidoz.postest.domain.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.postest.data.export.ExportException
import su.kidoz.postest.data.export.PostmanExporter
import su.kidoz.postest.domain.model.RequestCollection
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Use case for exporting collections to various formats.
 * Currently supports:
 * - Postman Collection v2.1.0
 */
class ExportCollectionUseCase(
    private val postmanExporter: PostmanExporter,
) {
    /**
     * Export a collection to a file in Postman v2.1.0 format.
     *
     * @param collection The collection to export
     * @param file The target file
     * @return Result indicating success or failure
     */
    suspend fun exportToPostman(
        collection: RequestCollection,
        file: File,
    ): Result<Unit> {
        logger.info { "Exporting collection '${collection.name}' to Postman format: ${file.absolutePath}" }

        if (!file.parentFile?.exists()!!) {
            file.parentFile?.mkdirs()
        }

        return postmanExporter.exportToFile(collection, file)
    }

    /**
     * Export a collection to JSON string in Postman v2.1.0 format.
     *
     * @param collection The collection to export
     * @return Result containing the JSON string or an error
     */
    fun exportToPostmanString(collection: RequestCollection): Result<String> =
        try {
            val jsonString = postmanExporter.export(collection)
            Result.success(jsonString)
        } catch (e: Exception) {
            logger.error(e) { "Failed to export collection to Postman format: ${e.message}" }
            Result.failure(ExportException("Failed to export collection: ${e.message}", e))
        }

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
