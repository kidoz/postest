package su.kidoz.postest.data.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class RecordCipherTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `encryption uses fresh nonces and round trips arbitrary text`() {
        RecordCipher("vault", key).use { cipher ->
            listOf("", "token-EXAMPLE-123", "日本語\u0000🔒").forEach { value ->
                val first = cipher.encrypt("table", "row", "column", value)
                val second = cipher.encrypt("table", "row", "column", value)
                assertNotEquals(first, second)
                assertEquals(value, cipher.decrypt("table", "row", "column", first))
                assertFalse(first.contains("token-EXAMPLE-123"))
            }
        }
    }

    @Test
    fun `wrong key vault row column and modified ciphertext fail closed`() {
        RecordCipher("vault", key).use { cipher ->
            val encrypted = cipher.encrypt("table", "row", "column", "EXAMPLE")
            val otherKey = RecordCipher("vault", ByteArray(32) { 99 })
            val otherVault = RecordCipher("other", key)
            listOf<() -> Unit>(
                { otherKey.decrypt("table", "row", "column", encrypted) },
                { otherVault.decrypt("table", "row", "column", encrypted) },
                { cipher.decrypt("other", "row", "column", encrypted) },
                { cipher.decrypt("table", "other", "column", encrypted) },
                { cipher.decrypt("table", "row", "other", encrypted) },
                { cipher.decrypt("table", "row", "column", encrypted.dropLast(4) + "AAAA") },
                { cipher.decrypt("table", "row", "column", "plaintext") },
                { cipher.decrypt("table", "row", "column", "postest:v2:unknown") },
            ).forEach { operation ->
                assertEquals(StorageProblem.INVALID_DATA, assertFailsWith<StorageException> { operation() }.problem)
            }
            otherKey.close()
            otherVault.close()
        }
    }

    @Test
    fun `recovery key restores the same cipher and closing disables access`() {
        val cipher = RecordCipher("vault", key)
        val recovery = RecordCipher.parseRecoveryKey(cipher.recoveryKey())
        val encrypted = cipher.encrypt("table", "row", "column", "EXAMPLE")
        RecordCipher("vault", recovery).use { restored -> assertEquals("EXAMPLE", restored.decrypt("table", "row", "column", encrypted)) }
        cipher.close()
        assertFailsWith<IllegalStateException> { cipher.recoveryKey() }
        assertFailsWith<StorageException> { RecordCipher.parseRecoveryKey("not-a-key") }
    }
}
