package su.kidoz.postest.data.import

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportLoggingTest {
    @Test
    fun `failed JSON and YAML imports omit input and parser causes from logs and errors`() {
        val marker = "synthetic-sensitive-import-marker"
        val cases =
            listOf(
                PostmanImporter::class.java to {
                    PostmanImporter().import("""{"info":{"name":{"token":"$marker"}},"item":[]}""")
                },
                PostmanV1Importer::class.java to {
                    PostmanV1Importer().import("""{"id":"test","name":{"token":"$marker"},"requests":[]}""")
                },
                OpenAPIImporter::class.java to {
                    OpenAPIImporter().import("""{"openapi":"3.0.0","info":{"title":{"token":"$marker"}}}""")
                },
                OpenAPIImporter::class.java to {
                    OpenAPIImporter().import("openapi: 3.0.0\ninfo: [$marker")
                },
            )
        cases.forEach { (type, import) ->
            val logger = LoggerFactory.getLogger(type) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            try {
                val error = assertNotNull(import().exceptionOrNull())
                assertFalse(error.message.orEmpty().contains(marker))
                assertNull(error.cause)
                assertTrue(appender.list.isNotEmpty())
                appender.list.forEach { event ->
                    assertFalse(event.formattedMessage.contains(marker))
                    assertNull(event.throwableProxy)
                }
            } finally {
                logger.detachAppender(appender)
                appender.stop()
            }
        }
    }
}
