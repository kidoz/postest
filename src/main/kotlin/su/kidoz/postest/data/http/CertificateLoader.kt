package su.kidoz.postest.data.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.tls.CertificateAndKey
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val logger = KotlinLogging.logger {}

private val PKCS12_EXTENSIONS = setOf("p12", "pfx")

object CertificateLoader {
    fun load(
        certPath: String,
        keyPath: String,
        passphrase: String,
    ): CertificateAndKey {
        val certFile = File(certPath)
        require(certFile.exists()) { "Certificate file does not exist: $certPath" }
        require(certFile.isFile) { "Certificate path is not a file: $certPath" }

        val extension = certFile.extension.lowercase()
        return if (extension in PKCS12_EXTENSIONS) {
            loadFromPkcs12(certFile, passphrase)
        } else {
            loadFromPem(certFile, keyPath, passphrase)
        }
    }

    private fun loadFromPkcs12(
        certFile: File,
        passphrase: String,
    ): CertificateAndKey {
        val keyStore = KeyStore.getInstance("PKCS12")
        val pass = passphrase.toCharArray()
        FileInputStream(certFile).use { fis ->
            keyStore.load(fis, pass)
        }

        val alias =
            keyStore.aliases().toList().firstOrNull()
                ?: throw IllegalArgumentException("PKCS12 file contains no entries")

        val certChain =
            keyStore
                .getCertificateChain(alias)
                ?.map { it as X509Certificate }
                ?.toTypedArray()
                ?: arrayOf(keyStore.getCertificate(alias) as X509Certificate)

        val key =
            keyStore.getKey(alias, pass) as? PrivateKey
                ?: throw IllegalArgumentException("PKCS12 file does not contain a private key")

        logger.info { "Loaded client certificate from PKCS12: ${certFile.name}" }
        return CertificateAndKey(certChain, key)
    }

    private fun loadFromPem(
        certFile: File,
        keyPath: String,
        passphrase: String,
    ): CertificateAndKey {
        require(keyPath.isNotBlank()) { "Private key file path is required for PEM certificates" }
        val keyFile = File(keyPath)
        require(keyFile.exists()) { "Private key file does not exist: $keyPath" }
        require(keyFile.isFile) { "Private key path is not a file: $keyPath" }

        val certificate = loadPemCertificate(certFile)
        val privateKey = loadPemPrivateKey(keyFile, passphrase)

        logger.info { "Loaded client certificate from PEM: ${certFile.name}" }
        return CertificateAndKey(arrayOf(certificate), privateKey)
    }

    private fun loadPemCertificate(certFile: File): X509Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        FileInputStream(certFile).use { fis ->
            return certFactory.generateCertificate(fis) as X509Certificate
        }
    }

    private fun loadPemPrivateKey(
        keyFile: File,
        passphrase: String,
    ): PrivateKey {
        val keyContent = keyFile.readText()
        val isEncrypted = keyContent.contains("ENCRYPTED PRIVATE KEY")

        // Extract only the base64 content between PEM markers
        val pemPattern = Regex("-----BEGIN [A-Z ]+-----([^-]+)-----END [A-Z ]+-----", RegexOption.DOT_MATCHES_ALL)
        val match =
            pemPattern.find(keyContent)
                ?: throw IllegalArgumentException("No valid PEM block found in key file")
        val keyBase64 = match.groupValues[1].replace(Regex("\\s"), "")

        val keyBytes = Base64.getDecoder().decode(keyBase64)

        return if (isEncrypted) {
            decryptPrivateKey(keyBytes, passphrase)
        } else {
            parsePrivateKey(keyBytes)
        }
    }

    private fun decryptPrivateKey(
        keyBytes: ByteArray,
        passphrase: String,
    ): PrivateKey {
        require(passphrase.isNotEmpty()) { "Passphrase is required for encrypted private keys" }

        val encryptedInfo = EncryptedPrivateKeyInfo(keyBytes)
        val pbeKeySpec = PBEKeySpec(passphrase.toCharArray())
        val secretKeyFactory = SecretKeyFactory.getInstance(encryptedInfo.algName)
        val secretKey = secretKeyFactory.generateSecret(pbeKeySpec)

        val cipher = Cipher.getInstance(encryptedInfo.algName)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedInfo.algParameters)

        val keySpec = encryptedInfo.getKeySpec(cipher)
        return tryParseWithAlgorithms(keySpec)
    }

    private fun parsePrivateKey(keyBytes: ByteArray): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return tryParseWithAlgorithms(keySpec)
    }

    private val SUPPORTED_ALGORITHMS = listOf("RSA", "EC", "Ed25519", "Ed448", "DSA")

    private fun tryParseWithAlgorithms(keySpec: PKCS8EncodedKeySpec): PrivateKey {
        for (algorithm in SUPPORTED_ALGORITHMS) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(keySpec)
            } catch (_: Exception) {
                // Try next algorithm
            }
        }
        throw IllegalArgumentException(
            "Unsupported private key algorithm. Supported: ${SUPPORTED_ALGORITHMS.joinToString(", ")}. " +
                "Key must be in PKCS#8 format. Convert with: " +
                "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem",
        )
    }
}
