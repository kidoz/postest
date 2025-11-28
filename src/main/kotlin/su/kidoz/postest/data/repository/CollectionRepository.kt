package su.kidoz.postest.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /**
     * Creates a new collection.
     * Uses incremental update - appends to existing list instead of full reload.
     */
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

            // Incremental update: append new collection
            _collections.update { currentList ->
                currentList + collection
            }

            collection
        }

    /**
     * Updates an existing collection.
     * Uses incremental update - replaces the specific collection instead of full reload.
     */
    suspend fun updateCollection(collection: RequestCollection) =
        withContext(Dispatchers.IO) {
            database.postestQueries.updateCollection(
                id = collection.id,
                name = collection.name,
                description = collection.description,
                auth_json = collection.auth?.let { json.encodeToString(it) },
                updated_at = System.currentTimeMillis(),
            )

            // Incremental update: replace the updated collection
            _collections.update { currentList ->
                currentList.map { c ->
                    if (c.id == collection.id) collection else c
                }
            }
        }

    /**
     * Deletes a collection.
     * Uses incremental update - filters out the collection instead of full reload.
     */
    suspend fun deleteCollection(collectionId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteCollection(collectionId)

            // Incremental update: remove the deleted collection
            _collections.update { currentList ->
                currentList.filter { it.id != collectionId }
            }
        }

    /**
     * Renames a collection.
     * Uses incremental update - updates the specific collection instead of full reload.
     */
    suspend fun renameCollection(
        collectionId: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        val updatedAt = System.currentTimeMillis()
        database.postestQueries.renameCollection(newName, updatedAt, collectionId)

        // Incremental update: update the collection name
        _collections.update { currentList ->
            currentList.map { c ->
                if (c.id == collectionId) c.copy(name = newName, updatedAt = updatedAt) else c
            }
        }
    }

    /**
     * Adds a request to a collection.
     * Uses incremental update - adds to nested structure instead of full reload.
     */
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

            // Incremental update: add item to collection's nested structure
            _collections.update { currentList ->
                currentList.map { c ->
                    if (c.id == collectionId) {
                        c.copy(
                            items = addItemToCollection(c.items, item, parentFolderId),
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        c
                    }
                }
            }

            item
        }

    /**
     * Adds a folder to a collection.
     * Uses incremental update - adds to nested structure instead of full reload.
     */
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

            // Incremental update: add folder to collection's nested structure
            _collections.update { currentList ->
                currentList.map { c ->
                    if (c.id == collectionId) {
                        c.copy(
                            items = addItemToCollection(c.items, folder, parentFolderId),
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        c
                    }
                }
            }

            folder
        }

    /**
     * Deletes a collection item (request or folder).
     * Uses incremental update - removes from nested structure instead of full reload.
     */
    suspend fun deleteCollectionItem(itemId: String) =
        withContext(Dispatchers.IO) {
            database.postestQueries.deleteCollectionItem(itemId)

            // Incremental update: remove item from all collections' nested structures
            _collections.update { currentList ->
                currentList.map { c ->
                    val newItems = removeItemFromCollection(c.items, itemId)
                    if (newItems != c.items) {
                        c.copy(items = newItems, updatedAt = System.currentTimeMillis())
                    } else {
                        c
                    }
                }
            }
        }

    /**
     * Renames a collection item (request or folder).
     * Uses incremental update - updates in nested structure instead of full reload.
     */
    suspend fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        database.postestQueries.renameCollectionItem(newName, itemId)

        // Incremental update: rename item in all collections' nested structures
        _collections.update { currentList ->
            currentList.map { c ->
                val newItems = renameItemInCollection(c.items, itemId, newName)
                if (newItems != c.items) {
                    c.copy(items = newItems, updatedAt = System.currentTimeMillis())
                } else {
                    c
                }
            }
        }
    }

    /**
     * Import a complete collection including all nested items.
     * Used for importing collections from external formats (e.g., Postman).
     * Uses incremental update - appends to existing list instead of full reload.
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

            // Incremental update: append imported collection
            _collections.update { currentList ->
                currentList + collection
            }

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

    // ========== Helper functions for incremental updates on nested structures ==========

    /**
     * Adds an item to a collection's nested structure.
     * If parentFolderId is null, adds to root level.
     * If parentFolderId is specified, adds inside that folder.
     */
    private fun addItemToCollection(
        items: List<CollectionItem>,
        newItem: CollectionItem,
        parentFolderId: String?,
    ): List<CollectionItem> =
        if (parentFolderId == null) {
            // Add to root level
            items + newItem
        } else {
            // Find the parent folder and add to it
            items.map { item ->
                when (item) {
                    is CollectionItem.Folder ->
                        if (item.id == parentFolderId) {
                            item.copy(items = item.items + newItem)
                        } else {
                            item.copy(items = addItemToCollection(item.items, newItem, parentFolderId))
                        }
                    is CollectionItem.Request -> item
                }
            }
        }

    /**
     * Removes an item from a collection's nested structure by ID.
     * Recursively searches through folders.
     */
    private fun removeItemFromCollection(
        items: List<CollectionItem>,
        itemId: String,
    ): List<CollectionItem> =
        items
            .filter { it.id != itemId }
            .map { item ->
                when (item) {
                    is CollectionItem.Folder ->
                        item.copy(items = removeItemFromCollection(item.items, itemId))
                    is CollectionItem.Request -> item
                }
            }

    /**
     * Renames an item in a collection's nested structure by ID.
     * Recursively searches through folders.
     */
    private fun renameItemInCollection(
        items: List<CollectionItem>,
        itemId: String,
        newName: String,
    ): List<CollectionItem> =
        items.map { item ->
            when (item) {
                is CollectionItem.Request ->
                    if (item.id == itemId) item.copy(name = newName) else item
                is CollectionItem.Folder ->
                    if (item.id == itemId) {
                        item.copy(name = newName)
                    } else {
                        item.copy(items = renameItemInCollection(item.items, itemId, newName))
                    }
            }
        }
}
