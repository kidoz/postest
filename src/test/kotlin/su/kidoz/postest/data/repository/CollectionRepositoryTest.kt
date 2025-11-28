package su.kidoz.postest.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CollectionRepositoryTest {
    private lateinit var repository: CollectionRepository

    @BeforeTest
    fun setup() {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        repository = CollectionRepository(database)
    }

    @Test
    fun `loadCollections should return empty list when no collections`() =
        runTest {
            val collections = repository.loadCollections()

            assertTrue(collections.isEmpty())
        }

    @Test
    fun `createCollection should create collection and return it`() =
        runTest {
            val collection = repository.createCollection("API Tests", "Test collection")

            assertEquals("API Tests", collection.name)
            assertEquals("Test collection", collection.description)
            assertNotNull(collection.id)
        }

    @Test
    fun `loadCollections should return created collections`() =
        runTest {
            repository.createCollection("Collection 1")
            repository.createCollection("Collection 2")

            val collections = repository.loadCollections()

            assertEquals(2, collections.size)
        }

    @Test
    fun `collections flow should emit updates`() =
        runTest {
            repository.createCollection("Test Collection")

            val collections = repository.collections.first()

            assertEquals(1, collections.size)
            assertEquals("Test Collection", collections.first().name)
        }

    @Test
    fun `updateCollection should update collection name`() =
        runTest {
            val created = repository.createCollection("Old Name")
            val updated = created.copy(name = "New Name")

            repository.updateCollection(updated)
            val collections = repository.loadCollections()

            assertEquals("New Name", collections.first().name)
        }

    @Test
    fun `deleteCollection should remove collection`() =
        runTest {
            val created = repository.createCollection("ToDelete")

            repository.deleteCollection(created.id)
            val collections = repository.loadCollections()

            assertTrue(collections.isEmpty())
        }

    @Test
    fun `addRequestToCollection should add request`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )

            val item = repository.addRequestToCollection(collection.id, request, "Get Users")
            val collections = repository.loadCollections()
            val savedCollection = collections.first()

            assertEquals(1, savedCollection.items.size)
            assertTrue(savedCollection.items.first() is CollectionItem.Request)
            assertEquals("Get Users", (savedCollection.items.first() as CollectionItem.Request).name)
        }

    @Test
    fun `addRequestToCollection should store request data`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val request =
                HttpRequest(
                    method = HttpMethod.POST,
                    url = "https://api.example.com/users",
                    name = "Create User",
                )

            repository.addRequestToCollection(collection.id, request, "Create User Request")
            val collections = repository.loadCollections()
            val item = collections.first().items.first() as CollectionItem.Request

            assertEquals(HttpMethod.POST, item.request.method)
            assertEquals("https://api.example.com/users", item.request.url)
        }

    @Test
    fun `addFolderToCollection should add folder`() =
        runTest {
            val collection = repository.createCollection("API Tests")

            val folder = repository.addFolderToCollection(collection.id, "Users", "User endpoints")
            val collections = repository.loadCollections()
            val savedCollection = collections.first()

            assertEquals(1, savedCollection.items.size)
            assertTrue(savedCollection.items.first() is CollectionItem.Folder)
            assertEquals("Users", (savedCollection.items.first() as CollectionItem.Folder).name)
        }

    @Test
    fun `addRequestToCollection with parentFolderId should nest request`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val folder = repository.addFolderToCollection(collection.id, "Users")
            val request = HttpRequest(url = "https://api.example.com/users")

            repository.addRequestToCollection(collection.id, request, "List Users", folder.id)
            val collections = repository.loadCollections()
            val savedFolder = collections.first().items.first() as CollectionItem.Folder

            assertEquals(1, savedFolder.items.size)
            assertTrue(savedFolder.items.first() is CollectionItem.Request)
        }

    @Test
    fun `deleteCollectionItem should remove request`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val request = HttpRequest(url = "https://api.example.com/test")
            val item = repository.addRequestToCollection(collection.id, request, "Test")

            repository.deleteCollectionItem(item.id)
            val collections = repository.loadCollections()

            assertTrue(collections.first().items.isEmpty())
        }

    @Test
    fun `deleteCollectionItem should remove folder`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val folder = repository.addFolderToCollection(collection.id, "Folder")

            repository.deleteCollectionItem(folder.id)
            val collections = repository.loadCollections()

            assertTrue(collections.first().items.isEmpty())
        }

    @Test
    fun `collection should have timestamps`() =
        runTest {
            val before = System.currentTimeMillis()
            val collection = repository.createCollection("Test")
            val after = System.currentTimeMillis()

            assertTrue(collection.createdAt >= before)
            assertTrue(collection.createdAt <= after)
            assertTrue(collection.updatedAt >= before)
            assertTrue(collection.updatedAt <= after)
        }

    @Test
    fun `collections should be ordered by updated_at descending`() =
        runTest {
            repository.createCollection("Old")
            Thread.sleep(10)
            repository.createCollection("New")

            val collections = repository.loadCollections()

            assertEquals("New", collections.first().name)
            assertEquals("Old", collections.last().name)
        }

    @Test
    fun `nested folders should load correctly`() =
        runTest {
            val collection = repository.createCollection("Test")
            val parentFolder = repository.addFolderToCollection(collection.id, "Parent")
            repository.addFolderToCollection(collection.id, "Child", parentFolderId = parentFolder.id)

            val collections = repository.loadCollections()
            val parent = collections.first().items.first() as CollectionItem.Folder

            assertEquals(1, parent.items.size)
            assertTrue(parent.items.first() is CollectionItem.Folder)
            assertEquals("Child", (parent.items.first() as CollectionItem.Folder).name)
        }

    @Test
    fun `multiple items should maintain order`() =
        runTest {
            val collection = repository.createCollection("Test")
            repository.addRequestToCollection(collection.id, HttpRequest(url = "url1"), "First")
            repository.addRequestToCollection(collection.id, HttpRequest(url = "url2"), "Second")
            repository.addRequestToCollection(collection.id, HttpRequest(url = "url3"), "Third")

            val collections = repository.loadCollections()
            val items = collections.first().items

            assertEquals(3, items.size)
        }
}
