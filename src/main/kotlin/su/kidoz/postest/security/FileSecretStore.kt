package su.kidoz.postest.security

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val logger = KotlinLogging.logger {}

/**
 * AES-GCM backed store that keeps ciphertext per-entry on disk under the user's home.
 * Master key is generated once and stored alongside the app data directory.
 */
class FileSecretStore(
    private val baseDir: Path,
    masterKeyPath: Path,
) : SecretStore {
    override val name: String = "file-aes-gcm"

    private val secureRandom = SecureRandom()
    private val masterKey: SecretKey = MasterKeyProvider(masterKeyPath).getOrCreate()

    init {
        Files.createDirectories(baseDir)
        val dirFile = baseDir.toFile()
        dirFile.setReadable(true, true)
        dirFile.setWritable(true, true)
        dirFile.setExecutable(true, true)
    }

    override fun store(
        key: String,
        value: String,
    ): Result<Unit> =
        runCatching {
            val sanitized = sanitize(key)
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(1 + iv.size + cipherBytes.size)
            payload[0] = 1 // version
            System.arraycopy(iv, 0, payload, 1, iv.size)
            System.arraycopy(cipherBytes, 0, payload, 1 + iv.size, cipherBytes.size)
            val encoded = Base64.getEncoder().encodeToString(payload)
            val target = baseDir.resolve("$sanitized.bin")
            Files.writeString(target, encoded)
        }

    override fun retrieve(key: String): Result<String?> =
        runCatching {
            val sanitized = sanitize(key)
            val target = baseDir.resolve("$sanitized.bin")
            if (!Files.exists(target)) return@runCatching null
            val encoded = Files.readString(target)
            val data = Base64.getDecoder().decode(encoded)
            if (data.isEmpty()) return@runCatching null
            val version = data[0].toInt()
            if (version != 1) {
                logger.warn { "Unknown secret format version: $version" }
                return@runCatching null
            }
            val iv = data.copyOfRange(1, 13)
            val cipherBytes = data.copyOfRange(13, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(cipherBytes)
            plain.toString(Charsets.UTF_8)
        }

    override fun delete(key: String): Result<Unit> =
        runCatching {
            val sanitized = sanitize(key)
            val target = baseDir.resolve("$sanitized.bin")
            Files.deleteIfExists(target)
        }

    private fun sanitize(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
