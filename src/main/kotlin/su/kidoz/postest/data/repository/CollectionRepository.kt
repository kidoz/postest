package su.kidoz.postest.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection
import java.util.UUID

private val logger = KotlinLogging.logger {}

class CollectionRepository(
    private val database: PostestDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _collections = MutableStateFlow<List<RequestCollection>>(emptyList())
    val collections: Flow<List<RequestCollection>> = _collections.asStateFlow()

    suspend fun loadCollections() =
        withContext(Dispatchers.IO) {
            val dbCollections = database.postestQueries.selectAllCollections().executeAsList()
            val result =
                dbCollections.map { dbCollection ->
                    val items = loadCollectionItems(dbCollection.id, null)
                    RequestCollection(
                        id = dbCollection.id,
                        name = dbCollection.name,
                        description = dbCollection.description,
                        items = items,
                        auth = dbCollection.auth_json?.let { json.decodeFromString<AuthConfig>(it) },
                        createdAt = dbCollection.created_at,
                        updatedAt = dbCollection.updated_at,
                    )
                }
            _collections.value = result
            result
        }

    private fun loadCollectionItems(
        collectionId: String,
        parentFolderId: String?,
    ): List<CollectionItem> {
        val dbItems =
            if (parentFolderId == null) {
                database.postestQueries.selectRootItemsByCollectionId(collectionId).executeAsList()
            } else {
                database.postestQueries.selectItemsByParentFolderId(parentFolderId).executeAsList()
            }

        return dbItems.map { dbItem ->
            when (dbItem.type) {
                "request" -> {
                    val request =
                        dbItem.request_json?.let { json.decodeFromString<HttpRequest>(it) }
                            ?: HttpRequest()
                    CollectionItem.Request(
                        id = dbItem.id,
                        name = dbItem.name,
                        request = request,
                    )
                }
                "folder" -> {
                    val children = loadCollectionItems(collectionId, dbItem.id)
                    CollectionItem.Folder(
                        id = dbItem.id,
                        name = dbItem.name,
                        description = dbItem.description,
                        items = children,
                    )
                }
                else -> {
                    logger.error { "Unknown collection item type: ${dbItem.type} for item ${dbItem.id}" }
                    throw IllegalStateException("Unknown collection item type: ${dbItem.type}")
                }
            }
        }
    }

    suspend fun createCollection(
        name: String,
        description: String = "",
    ): RequestCollection =
        withContext(Dispatchers.IO) {
            val collection =
                RequestCollection(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )

            database.postestQueries.insertCollection(
                id = collection.id,
                name = collection.name,
                description = collection.description,
                auth_json = collection.auth?.let { json.encodeToString(it) },
                created_at = collection.createdAt,
                updated_at = collection.updatedAt,
            )

            loadCollections()
            collection
        }

    suspend fun updateCollection(collection: RequestCollection) =
        withContext(Dispatchers.IO) {
            database.postestQueries.updateCollection(
                id = collection.id,
                name = collection.name,
                description = collection.description,
                auth_json = collection.auth?.let { json.encodeToString(it) },
                updated_at = System.currentTimeMillis(),
            )
            loadCollections()
        }

    suspend fun deleteCollection(collectionId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteCollection(collectionId)
            loadCollections()
        }

    suspend fun renameCollection(
        collectionId: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        database.postestQueries.renameCollection(newName, System.currentTimeMillis(), collectionId)
        loadCollections()
    }

    suspend fun addRequestToCollection(
        collectionId: String,
        request: HttpRequest,
        name: String,
        parentFolderId: String? = null,
    ): CollectionItem.Request =
        withContext(Dispatchers.IO) {
            val item =
                CollectionItem.Request(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    request = request,
                )

            val position =
                database.postestQueries
                    .selectItemsByCollectionId(collectionId)
                    .executeAsList()
                    .size

            database.postestQueries.insertCollectionItem(
                id = item.id,
                collection_id = collectionId,
                parent_folder_id = parentFolderId,
                type = "request",
                name = item.name,
                description = "",
                request_json = json.encodeToString(request),
                position = position.toLong(),
            )

            loadCollections()
            item
        }

    suspend fun addFolderToCollection(
        collectionId: String,
        name: String,
        description: String = "",
        parentFolderId: String? = null,
    ): CollectionItem.Folder =
        withContext(Dispatchers.IO) {
            val folder =
                CollectionItem.Folder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description,
                )

            val position =
                database.postestQueries
                    .selectItemsByCollectionId(collectionId)
                    .executeAsList()
                    .size

            database.postestQueries.insertCollectionItem(
                id = folder.id,
                collection_id = collectionId,
                parent_folder_id = parentFolderId,
                type = "folder",
                name = folder.name,
                description = description,
                request_json = null,
                position = position.toLong(),
            )

            loadCollections()
            folder
        }

    suspend fun deleteCollectionItem(itemId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteCollectionItem(itemId)
            loadCollections()
        }

    suspend fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        database.postestQueries.renameCollectionItem(newName, itemId)
        loadCollections()
    }

    /**
     * Import a complete collection including all nested items.
     * Used for importing collections from external formats (e.g., Postman).
     */
    suspend fun importCollection(collection: RequestCollection): RequestCollection =
        withContext(Dispatchers.IO) {
            logger.info { "Importing collection '${collection.name}' with ${collection.items.size} top-level items" }

            // Insert the collection
            database.postestQueries.insertCollection(
                id = collection.id,
                name = collection.name,
                description = collection.description,
                auth_json = collection.auth?.let { json.encodeToString(it) },
                created_at = collection.createdAt,
                updated_at = collection.updatedAt,
            )

            // Insert all items recursively
            insertItemsRecursively(collection.id, collection.items, parentFolderId = null)

            loadCollections()
            collection
        }

    private fun insertItemsRecursively(
        collectionId: String,
        items: List<CollectionItem>,
        parentFolderId: String?,
    ) {
        items.forEachIndexed { index, item ->
            when (item) {
                is CollectionItem.Request -> {
                    database.postestQueries.insertCollectionItem(
                        id = item.id,
                        collection_id = collectionId,
                        parent_folder_id = parentFolderId,
                        type = "request",
                        name = item.name,
                        description = "",
                        request_json = json.encodeToString(item.request),
                        position = index.toLong(),
                    )
                }
                is CollectionItem.Folder -> {
                    database.postestQueries.insertCollectionItem(
                        id = item.id,
                        collection_id = collectionId,
                        parent_folder_id = parentFolderId,
                        type = "folder",
                        name = item.name,
                        description = item.description,
                        request_json = null,
                        position = index.toLong(),
                    )
                    // Recursively insert children
                    insertItemsRecursively(collectionId, item.items, parentFolderId = item.id)
                }
            }
        }
    }
}
