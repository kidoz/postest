package su.kidoz.postest.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDisplayNameTest {
    @Test
    fun `an explicit name is preserved`() {
        assertEquals("List orders", requestDisplayName("List orders", "https://example.test/orders"))
    }

    @Test
    fun `unnamed requests show the path without credentials query or fragment`() {
        assertEquals("/orders", requestDisplayName("", "https://demo:example@example.test/orders?token=example#fragment"))
        assertEquals("example.test", requestDisplayName("", "https://example.test?token=example"))
    }

    @Test
    fun `relative and environment based endpoints have useful labels`() {
        assertEquals("/orders", requestDisplayName(" ", "{{baseUrl}}/orders?limit=10"))
        assertEquals("/orders", requestDisplayName("", "/orders#details"))
        assertEquals("/orders/{{id}}", requestDisplayName("", "{{baseUrl}}/orders/{{id}}?limit=10"))
    }

    @Test
    fun `the default request name is replaced by the URL path`() {
        assertEquals("/orders", requestDisplayName("Untitled Request", "https://example.test/orders?token=example"))
        assertEquals("/orders/{{id}}", requestDisplayName("Untitled Request", "{{baseUrl}}/orders/{{id}}"))
        assertEquals("Untitled Request", requestDisplayName("Untitled Request", ""))
    }

    @Test
    fun `a URL without a path uses the host instead of a bare slash`() {
        assertEquals("google.com", requestDisplayName("", "http://google.com"))
        assertEquals("example.test", requestDisplayName("Untitled Request", "https://example.test/"))
        assertEquals("localhost:8080", requestDisplayName("", "http://localhost:8080"))
        assertEquals("example.test", requestDisplayName("", "https://demo:example@example.test?token=example"))
    }

    @Test
    fun `a URL made only of a variable uses the variable name`() {
        assertEquals("{{baseUrl}}", requestDisplayName("", "{{baseUrl}}"))
        assertEquals("{{baseUrl}}", requestDisplayName("", "{{baseUrl}}/"))
    }

    @Test
    fun `invalid and empty URLs have a stable fallback`() {
        listOf("", "not a url", "//demo:example@example.test", "https://[invalid").forEach {
            assertEquals("Untitled Request", requestDisplayName("", it))
        }
    }
}
