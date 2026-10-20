package su.kidoz.postest.domain.port

import su.kidoz.postest.domain.model.RequestCollection

interface CollectionDecoder {
    fun recognizes(content: String): Boolean

    fun decode(content: String): Result<RequestCollection>
}

interface CollectionEncoder {
    fun encode(collection: RequestCollection): Result<String>
}

class ImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class ExportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
