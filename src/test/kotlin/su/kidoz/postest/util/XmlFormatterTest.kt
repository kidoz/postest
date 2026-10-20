package su.kidoz.postest.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlFormatterTest {
    @Test
    fun `format preserves significant text whitespace`() {
        val formatted = XmlFormatter.format("<root><item>  keep  </item></root>")

        assertTrue(formatted.contains(">  keep  </item>"))
    }

    @Test
    fun `format escapes namespace URIs`() {
        val input = """<root xmlns:x="a&amp;b"><x:item/></root>"""

        val formatted = XmlFormatter.format(input)

        assertTrue(formatted.contains("""xmlns:x="a&amp;b""""))
        assertTrue(XmlFormatter.isValid(formatted))
    }

    @Test
    fun `format leaves xml space preserve documents unchanged`() {
        val input = """<root xml:space="preserve"><item>  keep  </item></root>"""

        assertEquals(input, XmlFormatter.format(input))
        assertEquals(input, XmlFormatter.minify(input))
    }

    @Test
    fun `minify preserves significant text whitespace`() {
        val minified = XmlFormatter.minify("<root><item>  keep  </item></root>")

        assertTrue(minified.contains(">  keep  </item>"))
    }
}
