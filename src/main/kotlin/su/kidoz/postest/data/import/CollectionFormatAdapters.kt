package su.kidoz.postest.data.import

import su.kidoz.postest.data.export.PostmanExporter
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.port.CollectionDecoder
import su.kidoz.postest.domain.port.CollectionEncoder
import su.kidoz.postest.domain.port.ExportException
import su.kidoz.postest.domain.port.ImportException as DomainImportException

class PostmanCollectionDecoder(
    private val importer: PostmanImporter,
) : CollectionDecoder {
    override fun recognizes(content: String): Boolean = importer.isPostmanCollection(content)

    override fun decode(content: String): Result<RequestCollection> = importer.import(content).safeImport()
}

class OpenAPICollectionDecoder(
    private val importer: OpenAPIImporter,
) : CollectionDecoder {
    override fun recognizes(content: String): Boolean = importer.isOpenAPISpec(content)

    override fun decode(content: String): Result<RequestCollection> = importer.import(content).safeImport()
}

class PostmanCollectionEncoder(
    private val exporter: PostmanExporter,
) : CollectionEncoder {
    override fun encode(collection: RequestCollection): Result<String> =
        try {
            Result.success(exporter.export(collection))
        } catch (_: Exception) {
            Result.failure(ExportException("Collection could not be encoded"))
        }
}

private fun Result<RequestCollection>.safeImport(): Result<RequestCollection> =
    fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(DomainImportException("Collection could not be decoded")) })
