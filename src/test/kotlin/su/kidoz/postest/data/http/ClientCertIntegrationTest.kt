package su.kidoz.postest.data.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end verification of .p12 with password through CertificateLoader -> HttpClientFactory.
 */
class ClientCertIntegrationTest {
    private fun generateP12(passphrase: String): java.io.File {
        val dir = java.io.File(System.getProperty("java.io.tmpdir"), "postest-e2e-${System.nanoTime()}")
        dir.mkdirs()
        val p12 = java.io.File(dir, "client.p12")
        val proc =
            ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias",
                "e2e",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                p12.absolutePath,
                "-storepass",
                passphrase,
                "-dname",
                "CN=E2E Test, O=Postest",
            ).redirectErrorStream(true).start()
        proc.waitFor()
        check(proc.exitValue() == 0) { proc.inputStream.bufferedReader().readText() }
        return p12
    }

    @Test
    fun `p12 with password loads and creates HttpClient`() {
        val passphrase = "s3cret!Pass"
        val p12 = generateP12(passphrase)
        try {
            // Load certificate
            val certAndKey = CertificateLoader.load(p12.absolutePath, "", passphrase)
            assertNotNull(certAndKey)
            assertEquals(1, certAndKey.certificateChain.size)
            assertEquals("RSA", certAndKey.key.algorithm)
            assertTrue(
                certAndKey.certificateChain[0]
                    .subjectX500Principal.name
                    .contains("E2E Test"),
            )

            // Create client with certificate - must not throw
            val client = HttpClientFactory.createWithClientCertificate(certAndKey)
            assertNotNull(client)
            client.close()
        } finally {
            p12.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `p12 with wrong password is rejected`() {
        val p12 = generateP12("correctpass")
        try {
            val result = runCatching { CertificateLoader.load(p12.absolutePath, "", "wrongpass") }
            assertTrue(result.isFailure, "Wrong password should fail")
        } finally {
            p12.parentFile.deleteRecursively()
        }
    }

    @Test
    fun `p12 with empty password on protected file is rejected`() {
        val p12 = generateP12("haspassword")
        try {
            val result = runCatching { CertificateLoader.load(p12.absolutePath, "", "") }
            assertTrue(result.isFailure, "Empty password on protected file should fail")
        } finally {
            p12.parentFile.deleteRecursively()
        }
    }
}
