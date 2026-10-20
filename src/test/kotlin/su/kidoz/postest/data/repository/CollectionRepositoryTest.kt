package su.kidoz.postest.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.Variable
import su.kidoz.postest.domain.model.VariableType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `deleteCollectionItem should persist collection timestamp`() =
        runTest {
            val first = repository.createCollection("First")
            val item = repository.addRequestToCollection(first.id, HttpRequest(url = "https://api.example.com/first"), "Request")
            Thread.sleep(10)
            val second = repository.createCollection("Second")

            Thread.sleep(10)
            repository.deleteCollectionItem(item.id)

            val loaded = repository.loadCollections()
            assertEquals(listOf(first.id, second.id), loaded.map { it.id })
            assertEquals(loaded.map { it.id }, repository.collections.first().map { it.id })
            assertEquals(
                loaded.first().updatedAt,
                repository.collections
                    .first()
                    .first()
                    .updatedAt,
            )
        }

    @Test
    fun `renameCollectionItem should persist collection timestamp`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val item = repository.addRequestToCollection(collection.id, HttpRequest(url = "https://api.example.com/test"), "Old")
            val before = repository.loadCollections().first().updatedAt

            Thread.sleep(10)
            repository.renameCollectionItem(item.id, "New")

            val loaded = repository.loadCollections().first()
            assertEquals("New", (loaded.items.first() as CollectionItem.Request).name)
            assertTrue(loaded.updatedAt > before)
            assertEquals(
                loaded.updatedAt,
                repository.collections
                    .first()
                    .first()
                    .updatedAt,
            )
        }

    @Test
    fun `addRequestToCollection should persist collection timestamp`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val before = repository.loadCollections().first().updatedAt

            Thread.sleep(10)
            repository.addRequestToCollection(collection.id, HttpRequest(url = "https://api.example.com/test"), "Test")

            val loaded = repository.loadCollections().first()
            assertTrue(loaded.updatedAt > before)
            assertEquals(
                loaded.updatedAt,
                repository.collections
                    .first()
                    .first()
                    .updatedAt,
            )
        }

    @Test
    fun `moveCollectionItem should move request out of folder`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val folder = repository.addFolderToCollection(collection.id, "Folder")
            val item =
                repository.addRequestToCollection(
                    collection.id,
                    HttpRequest(url = "https://api.example.com/test"),
                    "Test",
                    folder.id,
                )
            val before = repository.loadCollections().first().updatedAt

            Thread.sleep(10)
            assertTrue(repository.moveCollectionItem(item.id, null))

            val loaded = repository.loadCollections().first()
            assertEquals(2, loaded.items.size)
            assertTrue(loaded.items.any { it.id == item.id })
            val savedFolder = loaded.items.first { it.id == folder.id } as CollectionItem.Folder
            assertTrue(savedFolder.items.isEmpty())
            assertTrue(loaded.updatedAt > before)
            assertEquals(
                loaded.updatedAt,
                repository.collections
                    .first()
                    .first()
                    .updatedAt,
            )
        }

    @Test
    fun `moveCollectionItem should move request into folder`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val folder = repository.addFolderToCollection(collection.id, "Folder")
            val item = repository.addRequestToCollection(collection.id, HttpRequest(), "Test")
            val before = repository.loadCollections().first().updatedAt

            Thread.sleep(10)
            assertTrue(repository.moveCollectionItem(item.id, folder.id))

            val loaded = repository.loadCollections().first()
            assertTrue(loaded.items.none { it.id == item.id })
            val savedFolder = loaded.items.first { it.id == folder.id } as CollectionItem.Folder
            assertEquals(listOf(item.id), savedFolder.items.map { it.id })
            assertTrue(loaded.updatedAt > before)
        }

    @Test
    fun `moveCollectionItem should move request between folders`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val source = repository.addFolderToCollection(collection.id, "Source")
            val target = repository.addFolderToCollection(collection.id, "Target")
            val item = repository.addRequestToCollection(collection.id, HttpRequest(), "Test", source.id)

            assertTrue(repository.moveCollectionItem(item.id, target.id))

            val loaded = repository.loadCollections().first()
            val savedSource = loaded.items.first { it.id == source.id } as CollectionItem.Folder
            val savedTarget = loaded.items.first { it.id == target.id } as CollectionItem.Folder
            assertTrue(savedSource.items.isEmpty())
            assertEquals(listOf(item.id), savedTarget.items.map { it.id })
        }

    @Test
    fun `moving requests and folders to root preserves appended order after reload`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val parent = repository.addFolderToCollection(collection.id, "Parent")
            val request = repository.addRequestToCollection(collection.id, HttpRequest(), "Nested request", parent.id)
            val folder = repository.addFolderToCollection(collection.id, "Nested folder", parentFolderId = parent.id)
            val child = repository.addRequestToCollection(collection.id, HttpRequest(), "Child", folder.id)
            val root = repository.addRequestToCollection(collection.id, HttpRequest(), "Root request")

            repository.moveCollectionItem(request.id, null)
            repository.moveCollectionItem(folder.id, null)

            val expectedOrder = listOf(parent.id, root.id, request.id, folder.id)
            val beforeReload = repository.collections.first().single()
            assertEquals(expectedOrder, beforeReload.items.map { it.id })
            val afterReload = repository.loadCollections().single()
            assertEquals(expectedOrder, afterReload.items.map { it.id })
            assertEquals(beforeReload.updatedAt, afterReload.updatedAt)
            assertEquals(listOf(child.id), (afterReload.items.last() as CollectionItem.Folder).items.map { it.id })
        }

    @Test
    fun `moveCollectionItem should move nested folder`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val parent = repository.addFolderToCollection(collection.id, "Parent")
            val child = repository.addFolderToCollection(collection.id, "Child", parentFolderId = parent.id)

            assertTrue(repository.moveCollectionItem(child.id, null))

            val loaded = repository.loadCollections().first()
            val savedParent = loaded.items.first { it.id == parent.id } as CollectionItem.Folder
            assertTrue(savedParent.items.isEmpty())
            assertTrue(loaded.items.any { it.id == child.id })
        }

    @Test
    fun `moveCollectionItem should reject moving a folder into its own subtree`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val parent = repository.addFolderToCollection(collection.id, "Parent")
            val child = repository.addFolderToCollection(collection.id, "Child", parentFolderId = parent.id)

            assertFalse(repository.moveCollectionItem(parent.id, child.id))
            assertFalse(repository.moveCollectionItem(parent.id, parent.id))

            val loaded = repository.loadCollections().first()
            val savedParent = loaded.items.first { it.id == parent.id } as CollectionItem.Folder
            assertEquals(listOf(child.id), savedParent.items.map { it.id })
        }

    @Test
    fun `moveCollectionItem should ignore items already in the target location`() =
        runTest {
            val collection = repository.createCollection("API Tests")
            val folder = repository.addFolderToCollection(collection.id, "Folder")
            val nested = repository.addRequestToCollection(collection.id, HttpRequest(), "Nested", folder.id)
            val root = repository.addRequestToCollection(collection.id, HttpRequest(), "Root")
            val before = repository.loadCollections().first().updatedAt

            Thread.sleep(10)
            assertFalse(repository.moveCollectionItem(nested.id, folder.id))
            assertFalse(repository.moveCollectionItem(root.id, null))

            assertEquals(before, repository.loadCollections().first().updatedAt)
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

    @Test
    fun `collection variables survive reload`() =
        runTest {
            val variables =
                listOf(
                    Variable(key = "baseUrl", value = "https://api.example.com"),
                    Variable(key = "apiKey", value = "secret", type = VariableType.SECRET),
                )
            repository.importCollection(RequestCollection(id = "vars", name = "API", variables = variables))

            val loaded = repository.loadCollections().single()
            assertEquals(variables, loaded.variables)
            assertEquals(
                variables,
                repository.collections
                    .first()
                    .single()
                    .variables,
            )
        }

    @Test
    fun `importCollection rolls back when an item insert fails`() =
        runTest {
            val duplicateItems =
                listOf(
                    CollectionItem.Request(id = "same-id", name = "One", request = HttpRequest()),
                    CollectionItem.Request(id = "same-id", name = "Two", request = HttpRequest()),
                )
            val collection = RequestCollection(id = "atomic", name = "API", items = duplicateItems)

            assertTrue(runCatching { repository.importCollection(collection) }.isFailure)
            assertTrue(repository.loadCollections().isEmpty())
            assertTrue(repository.collections.first().isEmpty())
        }
}
