package su.kidoz.postest.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.postest.domain.port.CollectionFiles
import su.kidoz.postest.domain.port.ExportException
import su.kidoz.postest.domain.port.ImportException
import java.io.File
import java.io.IOException

class LocalCollectionFiles : CollectionFiles {
    override suspend fun read(
        path: String,
        maxBytes: Int,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            require(maxBytes in 1 until Int.MAX_VALUE)
            try {
                val file = File(path)
                if (!file.isFile || !file.canRead()) return@withContext Result.failure(ImportException("Collection file cannot be read"))
                // Bound the actual read, including files that grow after their length was checked.
                val bytes = file.inputStream().use { it.readNBytes(maxBytes + 1) }
                if (bytes.size > maxBytes) {
                    Result.failure(ImportException("Collection file is too large"))
                } else {
                    Result.success(bytes.toString(Charsets.UTF_8))
                }
            } catch (_: IOException) {
                Result.failure(ImportException("Collection file cannot be read"))
            }
        }

    override suspend fun write(
        path: String,
        content: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
                Result.success(Unit)
            } catch (_: IOException) {
                Result.failure(ExportException("Collection file cannot be written"))
            }
        }
}
