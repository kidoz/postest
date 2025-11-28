package su.kidoz.postest.util

import com.fasterxml.aalto.stax.InputFactoryImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.StringReader
import java.io.StringWriter
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

private val logger = KotlinLogging.logger {}

/**
 * XML formatter using Aalto-XML for high-performance parsing.
 */
object XmlFormatter {
    private val inputFactory = InputFactoryImpl()

    private inline fun <T> XMLStreamReader.use(block: (XMLStreamReader) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    fun format(
        xmlString: String,
        indent: Int = 2,
    ): String =
        try {
            val indentStr = " ".repeat(indent)
            val writer = StringWriter()

            inputFactory.createXMLStreamReader(StringReader(xmlString)).use { reader ->
                var depth = 0
                var lastEvent = -1
                var hasContent = false

                while (reader.hasNext()) {
                    val event = reader.next()

                    when (event) {
                        XMLStreamConstants.START_DOCUMENT -> {
                            val version = reader.version ?: "1.0"
                            val encoding = reader.characterEncodingScheme ?: "UTF-8"
                            writer.append("<?xml version=\"$version\" encoding=\"$encoding\"?>")
                            writer.appendLine()
                        }

                        XMLStreamConstants.START_ELEMENT -> {
                            if (lastEvent == XMLStreamConstants.START_ELEMENT && !hasContent) {
                                writer.appendLine()
                            }
                            writer.append(indentStr.repeat(depth))
                            writer.append("<")
                            val prefix = reader.prefix
                            if (!prefix.isNullOrEmpty()) {
                                writer.append(prefix).append(":")
                            }
                            writer.append(reader.localName)

                            // Write namespaces
                            for (i in 0 until reader.namespaceCount) {
                                val nsPrefix = reader.getNamespacePrefix(i)
                                val nsUri = reader.getNamespaceURI(i)
                                if (nsPrefix.isNullOrEmpty()) {
                                    writer.append(" xmlns=\"$nsUri\"")
                                } else {
                                    writer.append(" xmlns:$nsPrefix=\"$nsUri\"")
                                }
                            }

                            // Write attributes
                            for (i in 0 until reader.attributeCount) {
                                val attrPrefix = reader.getAttributePrefix(i)
                                val attrName = reader.getAttributeLocalName(i)
                                val attrValue = escapeXml(reader.getAttributeValue(i))
                                if (!attrPrefix.isNullOrEmpty()) {
                                    writer.append(" $attrPrefix:$attrName=\"$attrValue\"")
                                } else {
                                    writer.append(" $attrName=\"$attrValue\"")
                                }
                            }
                            writer.append(">")
                            depth++
                            hasContent = false
                        }

                        XMLStreamConstants.END_ELEMENT -> {
                            depth--
                            if (lastEvent != XMLStreamConstants.CHARACTERS || !hasContent) {
                                if (lastEvent != XMLStreamConstants.START_ELEMENT) {
                                    writer.append(indentStr.repeat(depth))
                                }
                            }
                            writer.append("</")
                            val prefix = reader.prefix
                            if (!prefix.isNullOrEmpty()) {
                                writer.append(prefix).append(":")
                            }
                            writer.append(reader.localName)
                            writer.append(">")
                            writer.appendLine()
                            hasContent = false
                        }

                        XMLStreamConstants.CHARACTERS -> {
                            val text = reader.text
                            if (text.isNotBlank()) {
                                writer.append(escapeXml(text.trim()))
                                hasContent = true
                            }
                        }

                        XMLStreamConstants.CDATA -> {
                            writer.append("<![CDATA[")
                            writer.append(reader.text)
                            writer.append("]]>")
                            hasContent = true
                        }

                        XMLStreamConstants.COMMENT -> {
                            writer.append(indentStr.repeat(depth))
                            writer.append("<!--")
                            writer.append(reader.text)
                            writer.append("-->")
                            writer.appendLine()
                        }

                        XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                            writer.append(indentStr.repeat(depth))
                            writer.append("<?")
                            writer.append(reader.piTarget)
                            val piData = reader.piData
                            if (!piData.isNullOrEmpty()) {
                                writer.append(" ").append(piData)
                            }
                            writer.append("?>")
                            writer.appendLine()
                        }
                    }
                    lastEvent = event
                }
            }

            writer.toString().trim()
        } catch (e: Exception) {
            logger.debug(e) { "Failed to format XML with Aalto: ${e.message}" }
            // Fallback to original string
            xmlString
        }

    fun minify(xmlString: String): String =
        try {
            val writer = StringWriter()

            inputFactory.createXMLStreamReader(StringReader(xmlString)).use { reader ->
                while (reader.hasNext()) {
                    val event = reader.next()

                    when (event) {
                        XMLStreamConstants.START_DOCUMENT -> {
                            val version = reader.version ?: "1.0"
                            val encoding = reader.characterEncodingScheme ?: "UTF-8"
                            writer.append("<?xml version=\"$version\" encoding=\"$encoding\"?>")
                        }

                        XMLStreamConstants.START_ELEMENT -> {
                            writer.append("<")
                            val prefix = reader.prefix
                            if (!prefix.isNullOrEmpty()) {
                                writer.append(prefix).append(":")
                            }
                            writer.append(reader.localName)

                            for (i in 0 until reader.namespaceCount) {
                                val nsPrefix = reader.getNamespacePrefix(i)
                                val nsUri = reader.getNamespaceURI(i)
                                if (nsPrefix.isNullOrEmpty()) {
                                    writer.append(" xmlns=\"$nsUri\"")
                                } else {
                                    writer.append(" xmlns:$nsPrefix=\"$nsUri\"")
                                }
                            }

                            for (i in 0 until reader.attributeCount) {
                                val attrPrefix = reader.getAttributePrefix(i)
                                val attrName = reader.getAttributeLocalName(i)
                                val attrValue = escapeXml(reader.getAttributeValue(i))
                                if (!attrPrefix.isNullOrEmpty()) {
                                    writer.append(" $attrPrefix:$attrName=\"$attrValue\"")
                                } else {
                                    writer.append(" $attrName=\"$attrValue\"")
                                }
                            }
                            writer.append(">")
                        }

                        XMLStreamConstants.END_ELEMENT -> {
                            writer.append("</")
                            val prefix = reader.prefix
                            if (!prefix.isNullOrEmpty()) {
                                writer.append(prefix).append(":")
                            }
                            writer.append(reader.localName)
                            writer.append(">")
                        }

                        XMLStreamConstants.CHARACTERS -> {
                            val text = reader.text.trim()
                            if (text.isNotEmpty()) {
                                writer.append(escapeXml(text))
                            }
                        }

                        XMLStreamConstants.CDATA -> {
                            writer.append("<![CDATA[")
                            writer.append(reader.text)
                            writer.append("]]>")
                        }

                        XMLStreamConstants.COMMENT -> {
                            writer.append("<!--")
                            writer.append(reader.text)
                            writer.append("-->")
                        }
                    }
                }
            }

            writer.toString()
        } catch (e: Exception) {
            logger.debug(e) { "Failed to minify XML: ${e.message}" }
            xmlString
        }

    fun isValid(xmlString: String): Boolean =
        try {
            inputFactory.createXMLStreamReader(StringReader(xmlString)).use { reader ->
                while (reader.hasNext()) {
                    reader.next()
                }
            }
            true
        } catch (e: Exception) {
            logger.debug(e) { "Invalid XML: ${e.message}" }
            false
        }

    private fun escapeXml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
