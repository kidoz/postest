package su.kidoz.postest.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * Exception thrown when the master key file is corrupted or unreadable.
 * Previously encrypted secrets cannot be decrypted without the original key.
 */
class MasterKeyCorruptedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MasterKeyProvider(
    private val keyPath: Path,
) {
    private val secureRandom = SecureRandom()

    /**
     * Gets the existing master key or creates a new one if it doesn't exist.
     * @throws MasterKeyCorruptedException if the existing key file is corrupted or unreadable
     */
    fun getOrCreate(): SecretKey {
        if (Files.exists(keyPath)) {
            return readKey()
        }
        return createNewKey()
    }

    /**
     * Creates a new master key, overwriting any existing one.
     * WARNING: This will make previously encrypted secrets unreadable!
     */
    fun resetKey(): SecretKey {
        logger.warn { "Resetting master key - previously encrypted secrets will be lost!" }
        if (Files.exists(keyPath)) {
            Files.delete(keyPath)
        }
        return createNewKey()
    }

    private fun createNewKey(): SecretKey {
        Files.createDirectories(keyPath.parent)
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        val encoded = Base64.getEncoder().encodeToString(keyBytes)
        Files.writeString(keyPath, encoded)
        keyPath.toFile().setReadable(true, true)
        keyPath.toFile().setWritable(true, true)
        logger.info { "Created new master key at ${keyPath.toAbsolutePath()}" }
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun readKey(): SecretKey {
        try {
            val encoded = Files.readString(keyPath)
            if (encoded.isBlank()) {
                throw MasterKeyCorruptedException("Master key file is empty: $keyPath")
            }
            val bytes = Base64.getDecoder().decode(encoded.trim())
            if (bytes.size != 32) {
                throw MasterKeyCorruptedException(
                    "Master key has invalid size: expected 32 bytes, got ${bytes.size}",
                )
            }
            return SecretKeySpec(bytes, "AES")
        } catch (e: MasterKeyCorruptedException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Base64 decoding failed
            logger.error(e) { "Master key file contains invalid Base64 data" }
            throw MasterKeyCorruptedException("Master key file is corrupted (invalid encoding)", e)
        } catch (e: IOException) {
            logger.error(e) { "Failed to read master key file" }
            throw MasterKeyCorruptedException("Cannot read master key file: ${e.message}", e)
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error reading master key" }
            throw MasterKeyCorruptedException("Unexpected error reading master key: ${e.message}", e)
        }
    }
}
