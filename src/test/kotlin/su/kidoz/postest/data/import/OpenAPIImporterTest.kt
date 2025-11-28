package su.kidoz.postest.data.import

import su.kidoz.postest.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAPIImporterTest {
    private val importer = OpenAPIImporter()

    // ========== Detection Tests ==========

    @Test
    fun `isOpenAPISpec should return true for OpenAPI 3_0 spec`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "Test API", "version": "1.0" },
                "paths": {}
            }
            """.trimIndent()

        assertTrue(importer.isOpenAPISpec(json))
    }

    @Test
    fun `isOpenAPISpec should return true for OpenAPI 3_1 spec`() {
        val json =
            """
            {
                "openapi": "3.1.0",
                "info": { "title": "Test API", "version": "1.0" },
                "paths": {}
            }
            """.trimIndent()

        assertTrue(importer.isOpenAPISpec(json))
    }

    @Test
    fun `isOpenAPISpec should return false for non-OpenAPI JSON`() {
        val json =
            """
            {
                "info": { "name": "Test" },
                "item": []
            }
            """.trimIndent()

        assertFalse(importer.isOpenAPISpec(json))
    }

    @Test
    fun `isOpenAPISpec should return false for OpenAPI 2_0 (Swagger)`() {
        val json =
            """
            {
                "swagger": "2.0",
                "info": { "title": "Test API", "version": "1.0" },
                "paths": {}
            }
            """.trimIndent()

        assertFalse(importer.isOpenAPISpec(json))
    }

    // ========== Import Tests ==========

    @Test
    fun `import should parse basic OpenAPI spec`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": {
                    "title": "Pet Store API",
                    "description": "A sample API",
                    "version": "1.0.0"
                },
                "paths": {}
            }
            """.trimIndent()

        val result = importer.import(json)
        assertTrue(result.isSuccess)

        val collection = result.getOrThrow()
        assertEquals("Pet Store API", collection.name)
        assertEquals("A sample API", collection.description)
    }

    @Test
    fun `import should parse paths and generate requests`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "servers": [{ "url": "https://api.example.com" }],
                "paths": {
                    "/users": {
                        "get": {
                            "summary": "List users",
                            "operationId": "listUsers"
                        },
                        "post": {
                            "summary": "Create user",
                            "operationId": "createUser"
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        assertTrue(result.isSuccess)

        val collection = result.getOrThrow()
        assertEquals(2, collection.items.size)

        val getRequest =
            collection.items.find {
                (it as? CollectionItem.Request)?.request?.method == HttpMethod.GET
            } as CollectionItem.Request
        assertEquals("List users", getRequest.name)
        assertTrue(getRequest.request.url.contains("/users"))

        val postRequest =
            collection.items.find {
                (it as? CollectionItem.Request)?.request?.method == HttpMethod.POST
            } as CollectionItem.Request
        assertEquals("Create user", postRequest.name)
    }

    @Test
    fun `import should group requests by tags into folders`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "tags": [
                    { "name": "Users", "description": "User operations" }
                ],
                "paths": {
                    "/users": {
                        "get": {
                            "summary": "List users",
                            "tags": ["Users"]
                        }
                    },
                    "/pets": {
                        "get": {
                            "summary": "List pets",
                            "tags": ["Pets"]
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        // Should have 2 folders: Users and Pets
        val usersFolder =
            collection.items.find {
                (it as? CollectionItem.Folder)?.name == "Users"
            } as? CollectionItem.Folder
        assertNotNull(usersFolder)
        assertEquals("User operations", usersFolder.description)
        assertEquals(1, usersFolder.items.size)

        val petsFolder =
            collection.items.find {
                (it as? CollectionItem.Folder)?.name == "Pets"
            } as? CollectionItem.Folder
        assertNotNull(petsFolder)
    }

    @Test
    fun `import should extract query parameters`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/users": {
                        "get": {
                            "summary": "List users",
                            "parameters": [
                                {
                                    "name": "limit",
                                    "in": "query",
                                    "description": "Max results",
                                    "required": false,
                                    "schema": { "type": "integer", "default": 10 }
                                },
                                {
                                    "name": "offset",
                                    "in": "query",
                                    "required": true
                                }
                            ]
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertEquals(2, request.queryParams.size)

        val limitParam = request.queryParams.find { it.key == "limit" }
        assertNotNull(limitParam)
        assertEquals("Max results", limitParam.description)
        assertFalse(limitParam.enabled) // not required

        val offsetParam = request.queryParams.find { it.key == "offset" }
        assertNotNull(offsetParam)
        assertTrue(offsetParam.enabled) // required
    }

    @Test
    fun `import should extract header parameters`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/users": {
                        "get": {
                            "summary": "List users",
                            "parameters": [
                                {
                                    "name": "X-Request-ID",
                                    "in": "header",
                                    "required": true
                                }
                            ]
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        val header = request.headers.find { it.key == "X-Request-ID" }
        assertNotNull(header)
        assertTrue(header.enabled)
    }

    @Test
    fun `import should handle path parameters`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "servers": [{ "url": "https://api.example.com" }],
                "paths": {
                    "/users/{userId}": {
                        "get": {
                            "summary": "Get user",
                            "parameters": [
                                {
                                    "name": "userId",
                                    "in": "path",
                                    "required": true,
                                    "schema": { "type": "string", "example": "123" }
                                }
                            ]
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        // Path parameter should be replaced with example value
        assertTrue(request.url.contains("123") || request.url.contains("{{userId}}"))
    }

    @Test
    fun `import should parse JSON request body`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/users": {
                        "post": {
                            "summary": "Create user",
                            "requestBody": {
                                "required": true,
                                "content": {
                                    "application/json": {
                                        "schema": {
                                            "type": "object",
                                            "properties": {
                                                "name": { "type": "string" },
                                                "email": { "type": "string", "format": "email" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertNotNull(request.body)
        assertTrue(request.body is RequestBody.Json)

        val jsonBody = (request.body as RequestBody.Json).content
        assertTrue(jsonBody.contains("name"))
        assertTrue(jsonBody.contains("email"))
    }

    @Test
    fun `import should parse form-urlencoded request body`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/login": {
                        "post": {
                            "summary": "Login",
                            "requestBody": {
                                "content": {
                                    "application/x-www-form-urlencoded": {
                                        "schema": {
                                            "type": "object",
                                            "required": ["username"],
                                            "properties": {
                                                "username": { "type": "string" },
                                                "password": { "type": "string" }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        assertTrue(request.body is RequestBody.FormUrlEncoded)
        val body = request.body as RequestBody.FormUrlEncoded
        assertEquals(2, body.fields.size)

        val usernameField = body.fields.find { it.key == "username" }
        assertNotNull(usernameField)
        assertTrue(usernameField.enabled) // required
    }

    @Test
    fun `import should extract baseUrl variable from servers`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "servers": [
                    { "url": "https://api.example.com/v1" }
                ],
                "paths": {}
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        val baseUrlVar = collection.variables.find { it.key == "baseUrl" }
        assertNotNull(baseUrlVar)
        assertEquals("https://api.example.com/v1", baseUrlVar.value)
    }

    @Test
    fun `import should handle bearer auth security scheme`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "security": [{ "bearerAuth": [] }],
                "components": {
                    "securitySchemes": {
                        "bearerAuth": {
                            "type": "http",
                            "scheme": "bearer"
                        }
                    }
                },
                "paths": {}
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        assertTrue(collection.auth is AuthConfig.Bearer)
    }

    @Test
    fun `import should handle apiKey auth security scheme`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "security": [{ "apiKeyAuth": [] }],
                "components": {
                    "securitySchemes": {
                        "apiKeyAuth": {
                            "type": "apiKey",
                            "name": "X-API-Key",
                            "in": "header"
                        }
                    }
                },
                "paths": {}
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()

        assertTrue(collection.auth is AuthConfig.ApiKey)
        val apiKey = collection.auth as AuthConfig.ApiKey
        assertEquals("X-API-Key", apiKey.key)
        assertEquals(AuthConfig.ApiKey.AddTo.HEADER, apiKey.addTo)
    }

    @Test
    fun `import should use explicit example from schema`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/users": {
                        "post": {
                            "summary": "Create user",
                            "requestBody": {
                                "content": {
                                    "application/json": {
                                        "example": {
                                            "name": "John Doe",
                                            "email": "john@example.com"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        val body = request.body as RequestBody.Json
        assertTrue(body.content.contains("John Doe"))
        assertTrue(body.content.contains("john@example.com"))
    }

    @Test
    fun `import should reject OpenAPI 2_0`() {
        val json =
            """
            {
                "openapi": "2.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {}
            }
            """.trimIndent()

        // First, it won't be detected as OpenAPI spec
        assertFalse(importer.isOpenAPISpec(json))
    }

    @Test
    fun `import should add Content-Type header for JSON body`() {
        val json =
            """
            {
                "openapi": "3.0.0",
                "info": { "title": "API", "version": "1.0" },
                "paths": {
                    "/users": {
                        "post": {
                            "summary": "Create user",
                            "requestBody": {
                                "content": {
                                    "application/json": {
                                        "schema": { "type": "object" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent()

        val result = importer.import(json)
        val collection = result.getOrThrow()
        val request = (collection.items[0] as CollectionItem.Request).request

        val contentType = request.headers.find { it.key.equals("Content-Type", ignoreCase = true) }
        assertNotNull(contentType)
        assertEquals("application/json", contentType.value)
    }
}
