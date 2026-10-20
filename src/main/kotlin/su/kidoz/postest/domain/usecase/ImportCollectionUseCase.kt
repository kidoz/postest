package su.kidoz.postest.domain.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.port.CollectionDecoder
import su.kidoz.postest.domain.port.CollectionFiles
import su.kidoz.postest.domain.port.CollectionRepository
import su.kidoz.postest.domain.port.ImportException

private val logger = KotlinLogging.logger {}

/**
 * Use case for importing collections from various formats.
 * Currently supports:
 * - Postman Collection v1.0, v2.0, v2.1
 * - OpenAPI 3.x specifications
 */
class ImportCollectionUseCase(
    private val collectionRepository: CollectionRepository,
    private val postmanImporter: CollectionDecoder,
    private val openAPIImporter: CollectionDecoder,
    private val files: CollectionFiles,
) {
    companion object {
        /** Maximum file size for import (50 MB) */
        private const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024
    }

    /**
     * Import a collection from a file.
     * Automatically detects the format based on content.
     *
     * @param path The file path to import
     * @return Result containing the imported collection or an error
     */
    suspend fun importFromFile(path: String): Result<RequestCollection> =
        files.read(path, MAX_FILE_SIZE_BYTES).fold(
            onSuccess = { importFromString(it) },
            onFailure = { Result.failure(it) },
        )

    /**
     * Import a collection from a JSON string.
     * Automatically detects the format based on content.
     *
     * @param content The JSON content to import
     * @return Result containing the imported collection or an error
     */
    suspend fun importFromString(content: String): Result<RequestCollection> {
        // Detect format and import
        // Check OpenAPI first since it has a more specific marker ("openapi": "3.x")
        return when {
            openAPIImporter.recognizes(content) -> {
                logger.info { "Detected OpenAPI specification format" }
                persist(openAPIImporter.decode(content))
            }

            postmanImporter.recognizes(content) -> {
                logger.info { "Detected Postman collection format" }
                persist(postmanImporter.decode(content))
            }

            else -> {
                logger.warn { "Unknown collection format" }
                Result.failure(
                    ImportException(
                        "Unknown or unsupported collection format.\n" +
                            "Supported formats:\n" +
                            "- Postman Collection v1.0, v2.0, v2.1\n" +
                            "- OpenAPI 3.x specification",
                    ),
                )
            }
        }
    }

    private suspend fun persist(result: Result<RequestCollection>): Result<RequestCollection> =
        result.fold(
            onSuccess = { collection ->
                try {
                    collectionRepository.importCollection(collection)
                    Result.success(collection)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger.error { "Failed to persist imported collection (${error::class.simpleName})" }
                    Result.failure(ImportException("Imported collection could not be saved"))
                }
            },
            onFailure = { error -> Result.failure(error) },
        )

    /**
     * Get list of supported import formats.
     */
    fun supportedFormats(): List<ImportFormat> =
        listOf(
            ImportFormat(
                name = "Postman Collection",
                extensions = listOf("json"),
                description = "Postman Collection v1.0, v2.0, v2.1 formats (JSON)",
            ),
            ImportFormat(
                name = "OpenAPI Specification",
                extensions = listOf("json", "yaml", "yml"),
                description = "OpenAPI 3.0/3.1 specifications (JSON or YAML)",
            ),
        )
}

data class ImportFormat(
    val name: String,
    val extensions: List<String>,
    val description: String,
)
