package su.kidoz.postest.data.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Versioned AES-GCM envelopes bound to the vault, row and column. No plaintext compatibility path. */
class RecordCipher(
    private val vaultId: String,
    key: ByteArray,
) : RecordCodec {
    override val mode = StorageMode.ENCRYPTED
    private val key = key.copyOf().also { require(it.size == KEY_BYTES) }
    private val random = SecureRandom()
    private var closed = false

    @Synchronized
    override fun encode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String {
        check(!closed)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, nonce, table, rowId, column)
        val plaintext = value.toByteArray(Charsets.UTF_8)
        return try {
            PREFIX + Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    override fun decode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String {
        check(!closed)
        try {
            if (!value.startsWith(PREFIX)) throw StorageException(StorageProblem.INVALID_DATA)
            val bytes = Base64.getDecoder().decode(value.substring(PREFIX.length))
            if (bytes.size < NONCE_BYTES + TAG_BYTES) throw StorageException(StorageProblem.INVALID_DATA)
            val cipher = cipher(Cipher.DECRYPT_MODE, bytes.copyOfRange(0, NONCE_BYTES), table, rowId, column)
            val plaintext = cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
            return try {
                plaintext.toString(Charsets.UTF_8)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: GeneralSecurityException) {
            throw StorageException(StorageProblem.INVALID_DATA)
        } catch (_: IllegalArgumentException) {
            throw StorageException(StorageProblem.INVALID_DATA)
        }
    }

    private fun cipher(
        mode: Int,
        nonce: ByteArray,
        table: String,
        rowId: String,
        column: String,
    ): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            // Length-prefix each component so identifiers cannot create ambiguous contexts.
            listOf(PREFIX, vaultId, table, rowId, column).forEach {
                val bytes = it.toByteArray(Charsets.UTF_8)
                updateAAD(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                updateAAD(bytes)
            }
        }

    @Synchronized
    override fun recoveryKey(): String {
        check(!closed)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key)
    }

    @Synchronized
    override fun close() {
        key.fill(0)
        closed = true
    }

    companion object {
        const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private const val PREFIX = "postest:v1:"

        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

        fun parseRecoveryKey(value: String): ByteArray =
            try {
                Base64.getUrlDecoder().decode(value.trim()).also {
                    if (it.size != KEY_BYTES) {
                        it.fill(0)
                        throw StorageException(StorageProblem.INVALID_DATA)
                    }
                }
            } catch (_: IllegalArgumentException) {
                throw StorageException(StorageProblem.INVALID_DATA)
            }
    }
}
