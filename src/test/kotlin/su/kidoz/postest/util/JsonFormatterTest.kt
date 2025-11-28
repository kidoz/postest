package su.kidoz.postest.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonFormatterTest {
    @Test
    fun `format should pretty print valid JSON`() {
        val input = """{"name":"John","age":30}"""
        val expected = """{
  "name": "John",
  "age": 30
}"""
        assertEquals(expected, JsonFormatter.format(input))
    }

    @Test
    fun `format should handle nested JSON`() {
        val input = """{"user":{"name":"John","address":{"city":"NYC"}}}"""
        val result = JsonFormatter.format(input)
        assertTrue(result.contains("\"user\""))
        assertTrue(result.contains("\"address\""))
        assertTrue(result.contains("\"city\""))
    }

    @Test
    fun `format should return original string for invalid JSON`() {
        val invalidJson = "not a json"
        assertEquals(invalidJson, JsonFormatter.format(invalidJson))
    }

    @Test
    fun `format should handle JSON arrays`() {
        val input = """[1,2,3]"""
        val result = JsonFormatter.format(input)
        assertTrue(result.contains("1"))
        assertTrue(result.contains("2"))
        assertTrue(result.contains("3"))
    }

    @Test
    fun `minify should remove whitespace from JSON`() {
        val input = """{
  "name": "John",
  "age": 30
}"""
        val expected = """{"name":"John","age":30}"""
        assertEquals(expected, JsonFormatter.minify(input))
    }

    @Test
    fun `minify should return original string for invalid JSON`() {
        val invalidJson = "not a json"
        assertEquals(invalidJson, JsonFormatter.minify(invalidJson))
    }

    @Test
    fun `isValid should return true for valid JSON object`() {
        assertTrue(JsonFormatter.isValid("""{"name":"John"}"""))
    }

    @Test
    fun `isValid should return true for valid JSON array`() {
        assertTrue(JsonFormatter.isValid("""[1, 2, 3]"""))
    }

    @Test
    fun `isValid should return false for invalid JSON`() {
        assertFalse(JsonFormatter.isValid("not a json"))
    }

    @Test
    fun `isValid should return false for empty string`() {
        assertFalse(JsonFormatter.isValid(""))
    }

    @Test
    fun `format should handle empty JSON object`() {
        val input = "{}"
        val result = JsonFormatter.format(input)
        assertEquals("{}", result.trim())
    }

    @Test
    fun `format should handle empty JSON array`() {
        val input = "[]"
        val result = JsonFormatter.format(input)
        assertEquals("[]", result.trim())
    }
}
