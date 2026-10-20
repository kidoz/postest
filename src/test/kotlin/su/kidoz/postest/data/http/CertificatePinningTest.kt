package su.kidoz.postest.data.http

import su.kidoz.postest.domain.model.CertificatePin
import su.kidoz.postest.domain.model.CertificatePinMode
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestFailureException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CertificatePinningTest {
    private val certificateBytes = "leaf-certificate".toByteArray()
    private val publicKeyBytes = "leaf-public-key".toByteArray()
    private val leaf = FakeCertificate(certificateBytes, publicKeyBytes)
    private val certDigest = MessageDigest.getInstance("SHA-256").digest(certificateBytes)
    private val keyDigest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)

    @Test
    fun `parses base64 hex url-safe and prefixed pins`() {
        val hex = certDigest.joinToString("") { "%02x".format(it) }
        val base64 = Base64.getEncoder().encodeToString(certDigest)
        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(certDigest)

        assertContentEquals(certDigest, parseCertificatePin(hex))
        assertContentEquals(certDigest, parseCertificatePin(hex.uppercase().chunked(2).joinToString(":")))
        assertContentEquals(certDigest, parseCertificatePin(base64))
        assertContentEquals(certDigest, parseCertificatePin(urlSafe))
        assertContentEquals(certDigest, parseCertificatePin("sha256/$base64"))
        assertEquals(null, parseCertificatePin(""))
        assertEquals(null, parseCertificatePin("not-a-pin"))
        assertEquals(null, parseCertificatePin(Base64.getEncoder().encodeToString(ByteArray(16))))
    }

    @Test
    fun `matching certificate pin passes after chain validation`() {
        val delegate = RecordingTrustManager()
        val manager = PinnedTrustManager(delegate, CertificatePinMode.CERTIFICATE, certDigest)

        manager.checkServerTrusted(arrayOf(leaf), "RSA")

        assertTrue(delegate.checked)
    }

    @Test
    fun `mismatched certificate pin fails after chain validation`() {
        val delegate = RecordingTrustManager()
        val manager = PinnedTrustManager(delegate, CertificatePinMode.CERTIFICATE, keyDigest)

        assertFailsWith<CertificateException> { manager.checkServerTrusted(arrayOf(leaf), "RSA") }
        assertTrue(delegate.checked)
    }

    @Test
    fun `public key mode hashes the public key`() {
        val manager = PinnedTrustManager(RecordingTrustManager(), CertificatePinMode.PUBLIC_KEY, keyDigest)

        manager.checkServerTrusted(arrayOf(leaf), "RSA")
    }

    @Test
    fun `normal chain validation runs before pinning`() {
        val delegate = RecordingTrustManager(reject = true)
        val manager = PinnedTrustManager(delegate, CertificatePinMode.CERTIFICATE, certDigest)

        assertFailsWith<CertificateException> { manager.checkServerTrusted(arrayOf(leaf), "RSA") }
    }

    @Test
    fun `invalid pin values fail before any connection`() {
        val failure = assertFailsWith<RequestFailureException> { createPinningTrustManager(CertificatePin(sha256 = "bad")) }

        assertEquals(RequestFailure.INVALID_CERTIFICATE_PIN, failure.reason)
    }

    private class RecordingTrustManager(
        private val reject: Boolean = false,
    ) : X509TrustManager {
        var checked = false

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) {
            checked = true
            if (reject) throw CertificateException("untrusted chain")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class FakeCertificate(
        private val encoded: ByteArray,
        private val keyBytes: ByteArray,
    ) : X509Certificate() {
        override fun getEncoded(): ByteArray = encoded

        override fun getPublicKey(): PublicKey =
            object : PublicKey {
                override fun getAlgorithm(): String = "RSA"

                override fun getFormat(): String = "X.509"

                override fun getEncoded(): ByteArray = keyBytes
            }

        override fun checkValidity() = throw UnsupportedOperationException()

        override fun checkValidity(date: Date?) = throw UnsupportedOperationException()

        override fun getVersion(): Int = 3

        override fun getSerialNumber(): BigInteger = BigInteger.ONE

        override fun getIssuerDN(): Principal = throw UnsupportedOperationException()

        override fun getSubjectDN(): Principal = throw UnsupportedOperationException()

        override fun getNotBefore(): Date = throw UnsupportedOperationException()

        override fun getNotAfter(): Date = throw UnsupportedOperationException()

        override fun getTBSCertificate(): ByteArray = throw UnsupportedOperationException()

        override fun getSignature(): ByteArray = throw UnsupportedOperationException()

        override fun getSigAlgName(): String = throw UnsupportedOperationException()

        override fun getSigAlgOID(): String = throw UnsupportedOperationException()

        override fun getSigAlgParams(): ByteArray = throw UnsupportedOperationException()

        override fun getIssuerUniqueID(): BooleanArray = throw UnsupportedOperationException()

        override fun getSubjectUniqueID(): BooleanArray = throw UnsupportedOperationException()

        override fun getKeyUsage(): BooleanArray = throw UnsupportedOperationException()

        override fun getBasicConstraints(): Int = -1

        override fun verify(key: PublicKey?) = throw UnsupportedOperationException()

        override fun verify(
            key: PublicKey?,
            sigProvider: String?,
        ) = throw UnsupportedOperationException()

        override fun toString(): String = "FakeCertificate"

        override fun getCriticalExtensionOIDs(): MutableSet<String>? = null

        override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null

        override fun getExtensionValue(oid: String?): ByteArray? = null

        override fun hasUnsupportedCriticalExtension(): Boolean = false
    }
}
