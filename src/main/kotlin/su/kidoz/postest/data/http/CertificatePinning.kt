package su.kidoz.postest.data.http

import su.kidoz.postest.domain.model.CertificatePin
import su.kidoz.postest.domain.model.CertificatePinMode
import su.kidoz.postest.domain.model.RequestFailure
import su.kidoz.postest.domain.model.RequestFailureException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private val defaultTrustManager: X509TrustManager by lazy {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        ?: error("No default X509 trust manager")
}

/**
 * Parses a SHA-256 pin as Base64 (standard or URL-safe), hex, or the `sha256/` prefix form.
 * Returns null for anything that is not a 32-byte digest.
 */
internal fun parseCertificatePin(value: String): ByteArray? {
    val trimmed = value.trim().removePrefix("sha256/").trim()
    if (trimmed.isEmpty()) return null
    val hex = trimmed.replace(":", "").replace(" ", "")
    if (hex.length == 64 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        return ByteArray(32) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
    val normalized = trimmed.replace('-', '+').replace('_', '/').trimEnd('=')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    return runCatching { Base64.getDecoder().decode(padded) }
        .getOrNull()
        ?.takeIf { it.size == 32 }
}

/** Builds a trust manager that performs normal chain validation and then checks the leaf pin. */
internal fun createPinningTrustManager(pin: CertificatePin): X509TrustManager {
    val expected = parseCertificatePin(pin.sha256) ?: throw RequestFailureException(RequestFailure.INVALID_CERTIFICATE_PIN)
    return PinnedTrustManager(defaultTrustManager, pin.mode, expected)
}

internal class PinnedTrustManager(
    private val delegate: X509TrustManager,
    private val mode: CertificatePinMode,
    private val expected: ByteArray,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        delegate.checkServerTrusted(chain, authType)
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Certificate pin mismatch")
        val encoded = if (mode == CertificatePinMode.CERTIFICATE) leaf.encoded else leaf.publicKey.encoded
        val actual = MessageDigest.getInstance("SHA-256").digest(encoded)
        if (!MessageDigest.isEqual(actual, expected)) {
            throw CertificateException("Certificate pin mismatch")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}
