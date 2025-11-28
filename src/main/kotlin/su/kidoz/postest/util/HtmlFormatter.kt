package su.kidoz.postest.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private val logger = KotlinLogging.logger {}

/**
 * Lightweight HTML pretty-printer using XML tooling.
 * Not a full HTML5 parser, but good enough for well-formed fragments/responses.
 */
object HtmlFormatter {
    private val documentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

    fun format(html: String): String =
        runCatching {
            val builder = documentBuilderFactory.newDocumentBuilder()
            val input = InputSource(StringReader(html))
            val document = builder.parse(input)

            val transformer =
                TransformerFactory
                    .newInstance()
                    .apply {
                        setAttribute("indent-number", 2)
                    }.newTransformer()
                    .apply {
                        setOutputProperty(OutputKeys.INDENT, "yes")
                        setOutputProperty(OutputKeys.METHOD, "xml")
                        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                    }

            val writer = StringWriter()
            transformer.transform(DOMSource(document), StreamResult(writer))
            writer.toString().trim()
        }.getOrElse { error ->
            logger.debug(error) { "Failed to format HTML; returning original" }
            html
        }
}
