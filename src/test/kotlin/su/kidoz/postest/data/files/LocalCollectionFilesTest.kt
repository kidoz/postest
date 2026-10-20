package su.kidoz.postest.data.files

import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalCollectionFilesTest {
    private val files = LocalCollectionFiles()

    private fun file(): File = File("build/collection-files-test/${UUID.randomUUID()}/collection.json")

    @Test fun `write creates parent directories and read round trips UTF8`() =
        runTest {
            val file = file()
            val text = "{\"name\":\"Пример\"}"
            assertTrue(files.write(file.path, text).isSuccess)
            assertEquals(text, files.read(file.path, 1024).getOrThrow())
        }

    @Test fun `read enforces a byte limit and returns fixed errors`() =
        runTest {
            val file = file()
            files.write(file.path, "ЯЯ").getOrThrow()
            val result = files.read(file.path, 3)
            assertTrue(result.isFailure)
            assertEquals("Collection file is too large", result.exceptionOrNull()?.message)
            assertFalse(result.exceptionOrNull()!!.message!!.contains(file.path))
        }

    @Test fun `missing file errors do not contain paths`() =
        runTest {
            val file = file()
            assertEquals("Collection file cannot be read", files.read(file.path, 1024).exceptionOrNull()?.message)
        }
}
