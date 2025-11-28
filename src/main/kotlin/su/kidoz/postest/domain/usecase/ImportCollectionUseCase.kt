package su.kidoz.postest.domain.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.postest.data.import.ImportException
import su.kidoz.postest.data.import.OpenAPIImporter
import su.kidoz.postest.data.import.PostmanImporter
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.domain.model.RequestCollection
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Use case for importing collections from various formats.
 * Currently supports:
 * - Postman Collection v1.0, v2.0, v2.1
 * - OpenAPI 3.x specifications
 */
class ImportCollectionUseCase(
    private val collectionRepository: CollectionRepository,
    private val postmanImporter: PostmanImporter,
    private val openAPIImporter: OpenAPIImporter,
) {
    companion object {
        /** Maximum file size for import (50 MB) */
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024
    }

    /**
     * Import a collection from a file.
     * Automatically detects the format based on content.
     *
     * @param file The file to import
     * @return Result containing the imported collection or an error
     */
    suspend fun importFromFile(file: File): Result<RequestCollection> {
        logger.info { "Importing collection from file: ${file.absolutePath}" }

        if (!file.exists()) {
            return Result.failure(ImportException("File not found: ${file.absolutePath}"))
        }

        if (!file.canRead()) {
            return Result.failure(ImportException("Cannot read file: ${file.absolutePath}"))
        }

        // Check file size to prevent memory exhaustion
        val fileSize = file.length()
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            val sizeMB = fileSize / (1024 * 1024)
            val maxMB = MAX_FILE_SIZE_BYTES / (1024 * 1024)
            logger.warn { "File too large for import: ${sizeMB}MB (max: ${maxMB}MB)" }
            return Result.failure(
                ImportException("File is too large (${sizeMB}MB). Maximum allowed size is ${maxMB}MB."),
            )
        }

        val content =
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                logger.error(e) { "Failed to read file: ${file.absolutePath}" }
                return Result.failure(ImportException("Failed to read file: ${e.message}", e))
            }

        return importFromString(content)
    }

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
            openAPIImporter.isOpenAPISpec(content) -> {
                logger.info { "Detected OpenAPI specification format" }
                val result = openAPIImporter.import(content)
                result.onSuccess { collection ->
                    collectionRepository.importCollection(collection)
                }
                result
            }
            postmanImporter.isPostmanCollection(content) -> {
                logger.info { "Detected Postman collection format" }
                val result = postmanImporter.import(content)
                result.onSuccess { collection ->
                    collectionRepository.importCollection(collection)
                }
                result
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
