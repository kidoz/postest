package su.kidoz.postest.data.http

import java.io.File
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CertificateLoaderTest {
    private fun createTempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "postest-cert-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun generatePkcs12(
        dir: File,
        passphrase: String,
    ): File {
        val p12File = File(dir, "client.p12")
        val process =
            ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias",
                "test",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "365",
                "-storetype",
                "PKCS12",
                "-keystore",
                p12File.absolutePath,
                "-storepass",
                passphrase,
                "-dname",
                "CN=Test Client",
            ).redirectErrorStream(true).start()
        process.waitFor()
        check(process.exitValue() == 0) { "keytool failed: ${process.inputStream.bufferedReader().readText()}" }
        return p12File
    }

    private fun generatePemFiles(dir: File): Pair<File, File> {
        // Generate PKCS12 first, then export to PEM using openssl
        val p12File = generatePkcs12(dir, "testpass")

        val certFile = File(dir, "client.crt")
        val keyFile = File(dir, "client.key")

        // Export certificate
        val certProcess =
            ProcessBuilder(
                "openssl",
                "pkcs12",
                "-in",
                p12File.absolutePath,
                "-clcerts",
                "-nokeys",
                "-out",
                certFile.absolutePath,
                "-password",
                "pass:testpass",
            ).redirectErrorStream(true).start()
        certProcess.waitFor()
        check(certProcess.exitValue() == 0) {
            "openssl cert export failed: ${certProcess.inputStream.bufferedReader().readText()}"
        }

        // Export private key (unencrypted PKCS8)
        val keyProcess =
            ProcessBuilder(
                "openssl",
                "pkcs12",
                "-in",
                p12File.absolutePath,
                "-nocerts",
                "-nodes",
                "-out",
                keyFile.absolutePath,
                "-password",
                "pass:testpass",
            ).redirectErrorStream(true).start()
        keyProcess.waitFor()
        check(keyProcess.exitValue() == 0) {
            "openssl key export failed: ${keyProcess.inputStream.bufferedReader().readText()}"
        }

        return certFile to keyFile
    }

    private fun isOpenSslAvailable(): Boolean =
        runCatching {
            ProcessBuilder("openssl", "version").start().waitFor() == 0
        }.getOrDefault(false)

    @Test
    fun `load from PKCS12 file`() {
        val dir = createTempDir()
        try {
            val passphrase = "testpass"
            val p12File = generatePkcs12(dir, passphrase)

            val result = CertificateLoader.load(p12File.absolutePath, "", passphrase)

            assertNotNull(result)
            assertEquals(1, result.certificateChain.size)
            assertNotNull(result.key)
            assertEquals("RSA", result.key.algorithm)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load from PEM files`() {
        if (!isOpenSslAvailable()) return // skip if openssl not available

        val dir = createTempDir()
        try {
            val (certFile, keyFile) = generatePemFiles(dir)

            val result = CertificateLoader.load(certFile.absolutePath, keyFile.absolutePath, "")

            assertNotNull(result)
            assertEquals(1, result.certificateChain.size)
            assertNotNull(result.key)
            assertEquals("RSA", result.key.algorithm)
            val x509 = result.certificateChain[0] as X509Certificate
            assertEquals("CN=Test Client", x509.subjectX500Principal.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load from PEM without key path fails`() {
        if (!isOpenSslAvailable()) return

        val dir = createTempDir()
        try {
            val (certFile, _) = generatePemFiles(dir)

            assertFailsWith<IllegalArgumentException> {
                CertificateLoader.load(certFile.absolutePath, "", "")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `load from non-existent file fails`() {
        assertFailsWith<IllegalArgumentException> {
            CertificateLoader.load("/nonexistent/path/cert.p12", "", "")
        }
    }

    @Test
    fun `load from PKCS12 with wrong passphrase fails`() {
        val dir = createTempDir()
        try {
            val p12File = generatePkcs12(dir, "correct")

            assertFailsWith<Exception> {
                CertificateLoader.load(p12File.absolutePath, "", "wrong")
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
