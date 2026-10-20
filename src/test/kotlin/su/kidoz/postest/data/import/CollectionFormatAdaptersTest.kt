package su.kidoz.postest.data.import

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import su.kidoz.postest.domain.port.ImportException as DomainImportException

class CollectionFormatAdaptersTest {
    @Test fun `decoder failures expose domain exceptions without parser content or causes`() {
        val decoders = listOf(PostmanCollectionDecoder(PostmanImporter()), OpenAPICollectionDecoder(OpenAPIImporter()))
        decoders.forEach { decoder ->
            val failure = decoder.decode("invalid synthetic content").exceptionOrNull()
            assertIs<DomainImportException>(failure)
            assertEquals("Collection could not be decoded", failure.message)
            assertNull(failure.cause)
        }
    }
}
