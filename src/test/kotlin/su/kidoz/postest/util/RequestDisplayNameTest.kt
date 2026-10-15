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
        assertEquals("/", requestDisplayName("", "https://example.test?token=example"))
    }

    @Test
    fun `relative and environment based endpoints have useful labels`() {
        assertEquals("/orders", requestDisplayName(" ", "{{baseUrl}}/orders?limit=10"))
        assertEquals("/orders", requestDisplayName("", "/orders#details"))
        assertEquals("/orders/{{id}}", requestDisplayName("", "{{baseUrl}}/orders/{{id}}?limit=10"))
    }

    @Test
    fun `invalid and empty URLs have a stable fallback`() {
        listOf("", "not a url", "//demo:example@example.test", "https://[invalid").forEach {
            assertEquals("Untitled Request", requestDisplayName("", it))
        }
    }
}
