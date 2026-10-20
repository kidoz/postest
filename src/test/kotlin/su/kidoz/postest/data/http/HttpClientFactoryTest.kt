package su.kidoz.postest.data.http

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientFactoryTest {
    @Test
    fun `credential bearing headers are treated as sensitive`() {
        listOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "Set-Cookie",
            "X-API-Key",
            "Api-Key",
            "X-Auth-Token",
            "X-Secret",
        ).forEach { name ->
            assertTrue(isSensitiveHeaderName(name), "$name should be redacted")
        }
    }

    @Test
    fun `ordinary headers are not redacted`() {
        listOf("Content-Type", "Accept", "User-Agent", "X-Request-Id").forEach { name ->
            assertFalse(isSensitiveHeaderName(name), "$name should not be redacted")
        }
    }
}
