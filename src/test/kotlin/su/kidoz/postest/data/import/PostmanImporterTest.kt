package su.kidoz.postest.data.import

import su.kidoz.postest.data.import.PostmanVersionDetector.PostmanVersion
import su.kidoz.postest.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostmanImporterTest {
    private val importer = PostmanImporter()

    // ========== Version Detection Tests ==========

    @Test
    fun `detectVersion should return V2_1 for v2_1_0 schema`() {
        val json =
            """
            {
                "info": {
                    "name": "Test",
                    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                },
                "item": []
            }
            """.trimIndent()

        assertEquals(PostmanVersion.V2_1, importer.detectVersion(json))
    }

    @Test
    fun `detectVersion should return V2_0 for v2_0_0 schema`() {
        val json =
            """
            {
                "info": {
                    "name": "Test",
                    "schema": "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
                },
                "item": []
            }
            """.trimIndent()

        assertEquals(PostmanVersion.V2_0, importer.detectVersion(json))
    }

    @Test
    fun `detectVersion should return V1 for v1_0_0 structure`() {
        val json =
            """
            {
                "id": "123",
                "name": "Test",
                "order": [],
                "requests": [],
                "folders": []
            }
            """.trimIndent()

        assertEquals(PostmanVersion.V1, importer.detectVersion(json))
    }

    @Test
    fun `detectVersion should return V1 for requests array`() {
        val json =
            """
            {
                "id": "123",
                "name": "Test",
                "requests": [
                    { "id": "req1", "name": "Request 1", "method": "GET", "url": "http://example.com" }
                ]
            }
            """.trimIndent()

        assertEquals(PostmanVersion.V1, importer.detectVersion(json))
    }

    // ========== V1.0 Import Tests ==========

    @Test
    fun `import should parse v1_0 collection with requests`() {
        val json =
            """
            {
                "id": "collection-123",
                "name": "My V1 API",
                "description": "V1 Description",
                "order": ["req-1", "req-2"],
                "requests": [
                    {
                        "id": "req-1",
                        "name": "Get Users",
                        "method": "GET",
                        "url": "https://api.example.com/users"
                    },
                    {
                        "id": "req-2",
                        "name": "Create User",
                        "method": "POST",
                        "url": "https://api.example.com/users"
                    }
                ],
                "folders": []
            }
            """.trimIndent()

        val result = importer.import(json)
        assertTrue(result.isSuccess)

        val collection = result.getOrThrow()
        assertEquals("My V1 API", collection.name)
        assertEquals("V1 Description", collection.description)
        assertEquals(2, collection.items.size)

        val request1 = collection.items[0] as CollectionItem.Request
        assertEquals("Get Users", request1.name)
        assertEquals(HttpMethod.GET, request1.request.method)
    }

    @Test
    fun `import should parse v1_0 collection with folders`() {
        val json =
            """
            {
                "id": "collection-123",
                "name": "V1 with Folders",
                "order": [],
                "folders_order": ["folder-1"],
                "requests": [
                    {
                        "id": "req-1",
                        "name": "Get User",
                        "method": "GET",
                        "url": "https://api.example.com/users/1",
                        "folder": "folder-1"
                    }
                ],
                "folders": [
                    {
                        "id": "folder-1",
                        "name": "Users",
                        "order": ["req-1"]
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        assertTrue(result.isSuccess)

        val collection = result.getOrThrow()
        assertEquals(1, collection.items.size)

        val folder = collection.items[0] as CollectionItem.Folder
        assertEquals("Users", folder.name)
        assertEquals(1, folder.items.size)

        val request = folder.items[0] as CollectionItem.Request
        assertEquals("Get User", request.name)
    }

    @Test
    fun `import should parse v1_0 string headers`() {
        val json =
            """
            {
                "id": "123",
                "name": "Test",
                "order": ["req-1"],
                "requests": [
                    {
                        "id": "req-1",
                        "name": "With Headers",
                        "method": "GET",
                        "url": "https://api.example.com",
                        "headers": "Content-Type: application/json\nAuthorization: Bearer token123"
                    }
                ],
                "folders": []
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(2, request.headers.size)
        assertEquals("Content-Type", request.headers[0].key)
        assertEquals("application/json", request.headers[0].value)
        assertEquals("Authorization", request.headers[1].key)
        assertEquals("Bearer token123", request.headers[1].value)
    }

    @Test
    fun `import should parse v1_0 headerData array`() {
        val json =
            """
            {
                "id": "123",
                "name": "Test",
                "order": ["req-1"],
                "requests": [
                    {
                        "id": "req-1",
                        "name": "With HeaderData",
                        "method": "GET",
                        "url": "https://api.example.com",
                        "headerData": [
                            { "key": "X-Custom", "value": "custom-value" },
                            { "key": "X-Disabled", "value": "disabled", "enabled": false }
                        ]
                    }
                ],
                "folders": []
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(2, request.headers.size)
        assertTrue(request.headers[0].enabled)
        assertFalse(request.headers[1].enabled)
    }

    @Test
    fun `import should parse v1_0 basic auth with helperAttributes`() {
        val json =
            """
            {
                "id": "123",
                "name": "Test",
                "order": ["req-1"],
                "requests": [
                    {
                        "id": "req-1",
                        "name": "Basic Auth",
                        "method": "GET",
                        "url": "https://api.example.com",
                        "currentHelper": "basicAuth",
                        "helperAttributes": {
                            "username": "user",
                            "password": "pass"
                        }
                    }
                ],
                "folders": []
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertTrue(request.auth is AuthConfig.Basic)
        val auth = request.auth as AuthConfig.Basic
        assertEquals("user", auth.username)
        assertEquals("pass", auth.password)
    }

    // ========== V2.x Import Tests (existing tests) ==========

    @Test
    fun `isPostmanCollection should return true for valid Postman collection`() {
        val json =
            """
            {
                "info": {
                    "name": "Test Collection",
                    "_postman_id": "123",
                    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                },
                "item": []
            }
            """.trimIndent()

        assertTrue(importer.isPostmanCollection(json))
    }

    @Test
    fun `isPostmanCollection should return false for invalid JSON`() {
        assertFalse(importer.isPostmanCollection("not json"))
        assertFalse(importer.isPostmanCollection("[]"))
        assertFalse(importer.isPostmanCollection("""{"foo": "bar"}"""))
    }

    @Test
    fun `import should parse basic collection`() {
        val json =
            """
            {
                "info": {
                    "name": "My API",
                    "description": "API Description"
                },
                "item": []
            }
            """.trimIndent()

        val result = importer.import(json)

        assertTrue(result.isSuccess)
        val collection = result.getOrThrow()
        assertEquals("My API", collection.name)
        assertEquals("API Description", collection.description)
        assertTrue(collection.items.isEmpty())
    }

    @Test
    fun `import should parse GET request`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Get Users",
                        "request": {
                            "method": "GET",
                            "url": "https://api.example.com/users"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        assertEquals(1, collection.items.size)
        val request = collection.items[0] as CollectionItem.Request
        assertEquals("Get Users", request.name)
        assertEquals(HttpMethod.GET, request.request.method)
        assertEquals("https://api.example.com/users", request.request.url)
    }

    @Test
    fun `import should parse POST request with JSON body`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Create User",
                        "request": {
                            "method": "POST",
                            "url": "https://api.example.com/users",
                            "body": {
                                "mode": "raw",
                                "raw": "{\"name\": \"John\"}",
                                "options": {
                                    "raw": { "language": "json" }
                                }
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(HttpMethod.POST, request.method)
        assertNotNull(request.body)
        assertTrue(request.body is RequestBody.Json)
        // JSON is auto-formatted on import
        val expectedJson =
            """
            {
              "name": "John"
            }
            """.trimIndent()
        assertEquals(expectedJson, (request.body as RequestBody.Json).content)
    }

    @Test
    fun `import should parse request with headers`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "With Headers",
                        "request": {
                            "method": "GET",
                            "url": "https://api.example.com",
                            "header": [
                                { "key": "Content-Type", "value": "application/json" },
                                { "key": "X-Custom", "value": "custom-value", "disabled": true }
                            ]
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(2, request.headers.size)
        assertEquals("Content-Type", request.headers[0].key)
        assertEquals("application/json", request.headers[0].value)
        assertTrue(request.headers[0].enabled)

        assertEquals("X-Custom", request.headers[1].key)
        assertFalse(request.headers[1].enabled)
    }

    @Test
    fun `import should parse request with query params`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "With Params",
                        "request": {
                            "method": "GET",
                            "url": {
                                "raw": "https://api.example.com/search?q=test&limit=10",
                                "query": [
                                    { "key": "q", "value": "test" },
                                    { "key": "limit", "value": "10" }
                                ]
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(2, request.queryParams.size)
        assertEquals("q", request.queryParams[0].key)
        assertEquals("test", request.queryParams[0].value)
        assertEquals("limit", request.queryParams[1].key)
        assertEquals("10", request.queryParams[1].value)
    }

    @Test
    fun `import should parse nested folders`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Users Folder",
                        "item": [
                            {
                                "name": "Get User",
                                "request": {
                                    "method": "GET",
                                    "url": "https://api.example.com/users/1"
                                }
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        assertEquals(1, collection.items.size)
        val folder = collection.items[0] as CollectionItem.Folder
        assertEquals("Users Folder", folder.name)
        assertEquals(1, folder.items.size)

        val request = folder.items[0] as CollectionItem.Request
        assertEquals("Get User", request.name)
    }

    @Test
    fun `import should parse basic auth`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Auth Request",
                        "request": {
                            "method": "GET",
                            "url": "https://api.example.com",
                            "auth": {
                                "type": "basic",
                                "basic": [
                                    { "key": "username", "value": "user" },
                                    { "key": "password", "value": "pass" }
                                ]
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertNotNull(request.auth)
        assertTrue(request.auth is AuthConfig.Basic)
        val basicAuth = request.auth as AuthConfig.Basic
        assertEquals("user", basicAuth.username)
        assertEquals("pass", basicAuth.password)
    }

    @Test
    fun `import should parse bearer auth`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Bearer Request",
                        "request": {
                            "method": "GET",
                            "url": "https://api.example.com",
                            "auth": {
                                "type": "bearer",
                                "bearer": [
                                    { "key": "token", "value": "my-token-123" }
                                ]
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertNotNull(request.auth)
        assertTrue(request.auth is AuthConfig.Bearer)
        assertEquals("my-token-123", (request.auth as AuthConfig.Bearer).token)
    }

    @Test
    fun `import should parse form urlencoded body`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Form Request",
                        "request": {
                            "method": "POST",
                            "url": "https://api.example.com/login",
                            "body": {
                                "mode": "urlencoded",
                                "urlencoded": [
                                    { "key": "username", "value": "john" },
                                    { "key": "password", "value": "secret" }
                                ]
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertTrue(request.body is RequestBody.FormUrlEncoded)
        val body = request.body as RequestBody.FormUrlEncoded
        assertEquals(2, body.fields.size)
        assertEquals("username", body.fields[0].key)
        assertEquals("john", body.fields[0].value)
    }

    @Test
    fun `import should parse collection variables`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [],
                "variable": [
                    { "key": "base_url", "value": "https://api.example.com" },
                    { "key": "api_key", "value": "secret123" }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        assertEquals(2, collection.variables.size)
        assertEquals("base_url", collection.variables[0].key)
        assertEquals("https://api.example.com", collection.variables[0].value)
    }

    @Test
    fun `import should handle URL as string`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "Simple URL",
                        "request": {
                            "method": "GET",
                            "url": "https://api.example.com/users"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals("https://api.example.com/users", request.url)
    }

    @Test
    fun `import should return failure for invalid JSON`() {
        val result = importer.import("not valid json")

        assertTrue(result.isFailure)
    }

    @Test
    fun `import should parse graphql body`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": [
                    {
                        "name": "GraphQL Query",
                        "request": {
                            "method": "POST",
                            "url": "https://api.example.com/graphql",
                            "body": {
                                "mode": "graphql",
                                "graphql": {
                                    "query": "query { users { id name } }",
                                    "variables": "{\"limit\": 10}"
                                }
                            }
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertTrue(request.body is RequestBody.GraphQL)
        val body = request.body as RequestBody.GraphQL
        assertEquals("query { users { id name } }", body.query)
        assertEquals("{\"limit\": 10}", body.variables)
    }
}
