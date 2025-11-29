package su.kidoz.postest.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.Variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostmanExporterTest {
    private val exporter = PostmanExporter()
    private val json = Json { ignoreUnknownKeys = true }

    // ========== Basic Export Tests ==========

    @Test
    fun `export should produce valid JSON with v2_1_0 schema`() {
        val collection = RequestCollection(name = "Test Collection")
        val result = exporter.export(collection)

        val parsed = json.parseToJsonElement(result).jsonObject
        val info = parsed["info"]!!.jsonObject
        val schema = info["schema"]!!.jsonPrimitive.content

        assertEquals(PostmanExporter.SCHEMA_V2_1, schema)
    }

    @Test
    fun `export should include collection name`() {
        val collection = RequestCollection(name = "My API Collection")
        val result = exporter.export(collection)

        val parsed = json.parseToJsonElement(result).jsonObject
        val info = parsed["info"]!!.jsonObject
        val name = info["name"]!!.jsonPrimitive.content

        assertEquals("My API Collection", name)
    }

    @Test
    fun `export should include collection description when present`() {
        val collection = RequestCollection(name = "Test", description = "A test collection")
        val result = exporter.export(collection)

        val parsed = json.parseToJsonElement(result).jsonObject
        val info = parsed["info"]!!.jsonObject
        val description = info["description"]!!.jsonPrimitive.content

        assertEquals("A test collection", description)
    }

    @Test
    fun `export should omit description when empty`() {
        val collection = RequestCollection(name = "Test", description = "")
        val result = exporter.export(collection)

        val parsed = json.parseToJsonElement(result).jsonObject
        val info = parsed["info"]!!.jsonObject

        assertNull(info["description"])
    }

    // ========== Request Export Tests ==========

    @Test
    fun `export should include GET request with correct method`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com/users",
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Get Users", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val firstItem = items[0].jsonObject
        val req = firstItem["request"]!!.jsonObject

        // Method defaults to GET and may not be encoded when encodeDefaults=false
        val method = req["method"]?.jsonPrimitive?.content ?: "GET"
        assertEquals("GET", method)
    }

    @Test
    fun `export should include POST request with JSON body`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://api.example.com/users",
                body = RequestBody.Json("""{"name": "John"}"""),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Create User", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val req = items[0].jsonObject["request"]!!.jsonObject
        val body = req["body"]!!.jsonObject

        assertEquals("raw", body["mode"]!!.jsonPrimitive.content)
        assertEquals("""{"name": "John"}""", body["raw"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export should include request headers`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                headers =
                    listOf(
                        KeyValue(key = "Content-Type", value = "application/json"),
                        KeyValue(key = "Authorization", value = "Bearer token123"),
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val req = items[0].jsonObject["request"]!!.jsonObject
        val headers = req["header"]!!.jsonArray

        assertEquals(2, headers.size)
        assertEquals("Content-Type", headers[0].jsonObject["key"]!!.jsonPrimitive.content)
        assertEquals("Authorization", headers[1].jsonObject["key"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export should include query parameters in URL`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com/search",
                queryParams =
                    listOf(
                        KeyValue(key = "q", value = "test"),
                        KeyValue(key = "limit", value = "10"),
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Search", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val req = items[0].jsonObject["request"]!!.jsonObject
        val url = req["url"]!!.jsonObject
        val query = url["query"]!!.jsonArray

        assertEquals(2, query.size)
        assertEquals("q", query[0].jsonObject["key"]!!.jsonPrimitive.content)
    }

    // ========== Folder Export Tests ==========

    @Test
    fun `export should include folders with nested requests`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com/users",
            )
        val folder =
            CollectionItem.Folder(
                name = "Users",
                items = listOf(CollectionItem.Request(name = "Get Users", request = request)),
            )
        val collection = RequestCollection(name = "Test", items = listOf(folder))

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val folderItem = items[0].jsonObject

        assertEquals("Users", folderItem["name"]!!.jsonPrimitive.content)
        assertNotNull(folderItem["item"])

        val nestedItems = folderItem["item"]!!.jsonArray
        assertEquals(1, nestedItems.size)
    }

    // ========== Auth Export Tests ==========

    @Test
    fun `export should include Basic auth`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                auth = AuthConfig.Basic(username = "user", password = "pass"),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val auth = items[0].jsonObject["auth"]!!.jsonObject

        assertEquals("basic", auth["type"]!!.jsonPrimitive.content)
        assertNotNull(auth["basic"])
    }

    @Test
    fun `export should include Bearer auth`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                auth = AuthConfig.Bearer(token = "my-token"),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val auth = items[0].jsonObject["auth"]!!.jsonObject

        assertEquals("bearer", auth["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export should include API Key auth`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                auth =
                    AuthConfig.ApiKey(
                        key = "X-API-Key",
                        value = "secret123",
                        addTo = AuthConfig.ApiKey.AddTo.HEADER,
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val auth = items[0].jsonObject["auth"]!!.jsonObject

        assertEquals("apikey", auth["type"]!!.jsonPrimitive.content)
    }

    // ========== Body Types Export Tests ==========

    @Test
    fun `export should handle form-urlencoded body`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://api.example.com/login",
                body =
                    RequestBody.FormUrlEncoded(
                        fields =
                            listOf(
                                KeyValue(key = "username", value = "john"),
                                KeyValue(key = "password", value = "secret"),
                            ),
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Login", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val body = items[0].jsonObject["request"]!!.jsonObject["body"]!!.jsonObject

        assertEquals("urlencoded", body["mode"]!!.jsonPrimitive.content)
        val urlencoded = body["urlencoded"]!!.jsonArray
        assertEquals(2, urlencoded.size)
    }

    @Test
    fun `export should handle form-data body`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://api.example.com/upload",
                body =
                    RequestBody.FormData(
                        fields =
                            listOf(
                                FormField(
                                    key = "name",
                                    value = "test",
                                    type = FormFieldType.TEXT,
                                ),
                            ),
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Upload", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val body = items[0].jsonObject["request"]!!.jsonObject["body"]!!.jsonObject

        assertEquals("formdata", body["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export should handle XML body`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://api.example.com/xml",
                body = RequestBody.Xml("<root><item>test</item></root>"),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "XML", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val body = items[0].jsonObject["request"]!!.jsonObject["body"]!!.jsonObject
        val options = body["options"]!!.jsonObject["raw"]!!.jsonObject

        assertEquals("xml", options["language"]!!.jsonPrimitive.content)
    }

    @Test
    fun `export should handle GraphQL body`() {
        val request =
            HttpRequest(
                method = HttpMethod.POST,
                url = "https://api.example.com/graphql",
                body =
                    RequestBody.GraphQL(
                        query = "query { users { id name } }",
                        variables = """{"limit": 10}""",
                    ),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "GraphQL", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val body = items[0].jsonObject["request"]!!.jsonObject["body"]!!.jsonObject

        assertEquals("graphql", body["mode"]!!.jsonPrimitive.content)
    }

    // ========== Variables Export Tests ==========

    @Test
    fun `export should include collection variables`() {
        val collection =
            RequestCollection(
                name = "Test",
                variables =
                    listOf(
                        Variable(key = "baseUrl", value = "https://api.example.com"),
                        Variable(key = "apiKey", value = "secret123"),
                    ),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val variables = parsed["variable"]!!.jsonArray

        assertEquals(2, variables.size)
        assertEquals("baseUrl", variables[0].jsonObject["key"]!!.jsonPrimitive.content)
    }

    // ========== URL Parsing Tests ==========

    @Test
    fun `export should parse URL components correctly`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com/v1/users",
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val url = items[0].jsonObject["request"]!!.jsonObject["url"]!!.jsonObject

        assertEquals("https", url["protocol"]!!.jsonPrimitive.content)

        val host = url["host"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(host.contains("api"))
        assertTrue(host.contains("example"))
        assertTrue(host.contains("com"))

        val path = url["path"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(path.contains("v1"))
        assertTrue(path.contains("users"))
    }

    // ========== Disabled Items Tests ==========

    @Test
    fun `export should mark disabled headers correctly`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                headers = listOf(KeyValue(key = "X-Debug", value = "true", enabled = false)),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val headers = items[0].jsonObject["request"]!!.jsonObject["header"]!!.jsonArray
        val disabled = headers[0].jsonObject["disabled"]!!.jsonPrimitive.content

        assertEquals("true", disabled)
    }

    @Test
    fun `export should mark disabled query params correctly`() {
        val request =
            HttpRequest(
                method = HttpMethod.GET,
                url = "https://api.example.com",
                queryParams = listOf(KeyValue(key = "debug", value = "true", enabled = false)),
            )
        val collection =
            RequestCollection(
                name = "Test",
                items = listOf(CollectionItem.Request(name = "Test", request = request)),
            )

        val result = exporter.export(collection)
        val parsed = json.parseToJsonElement(result).jsonObject
        val items = parsed["item"]!!.jsonArray
        val req = items[0].jsonObject["request"]!!.jsonObject
        val url = req["url"]!!.jsonObject
        val query = url["query"]!!.jsonArray
        val disabled = query[0].jsonObject["disabled"]!!.jsonPrimitive.content

        assertEquals("true", disabled)
    }
}
