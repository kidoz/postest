package su.kidoz.postest.data.repository

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.domain.model.AuthConfig
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection
import su.kidoz.postest.domain.model.Variable
import java.util.UUID
import su.kidoz.postest.domain.port.CollectionRepository as CollectionRepositoryPort

/** SQLite owns saved collections; observers always rebuild from committed rows. */
class CollectionRepository(
    private val database: PostestDatabase,
) : CollectionRepositoryPort {
    private val queries = database.postestQueries
    private val json = Json { ignoreUnknownKeys = true }
    override val collections: Flow<List<RequestCollection>> = queries.observeCollectionChanges().asFlow().map { loadCollections() }

    override suspend fun loadCollections(): List<RequestCollection> =
        withContext(Dispatchers.IO) {
            database.transactionWithResult {
                val children = queries.selectAllCollectionItems().executeAsList().groupBy { it.parent_folder_id }

                fun items(
                    collectionId: String,
                    parentId: String?,
                ): List<CollectionItem> =
                    children[parentId].orEmpty().filter { it.collection_id == collectionId }.map { row ->
                        when (row.type) {
                            "request" -> {
                                CollectionItem.Request(
                                    id = row.id,
                                    name = row.name,
                                    request = row.request_json?.let { json.decodeFromString<HttpRequest>(it) } ?: HttpRequest(),
                                )
                            }

                            "folder" -> {
                                CollectionItem.Folder(
                                    id = row.id,
                                    name = row.name,
                                    description = row.description,
                                    items = items(collectionId, row.id),
                                )
                            }

                            else -> {
                                error("Unknown collection item type")
                            }
                        }
                    }
                queries.selectAllCollections().executeAsList().map { row ->
                    RequestCollection(
                        id = row.id,
                        name = row.name,
                        description = row.description,
                        items = items(row.id, null),
                        variables = row.variables_json?.let { json.decodeFromString<List<Variable>>(it) }.orEmpty(),
                        auth = row.auth_json?.let { json.decodeFromString<AuthConfig>(it) },
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                    )
                }
            }
        }

    override suspend fun createCollection(
        name: String,
        description: String,
    ): RequestCollection = importCollection(RequestCollection(name = name, description = description))

    override suspend fun updateCollection(collection: RequestCollection) {
        withContext(Dispatchers.IO) {
            queries.updateCollection(
                id = collection.id,
                name = collection.name,
                description = collection.description,
                auth_json = collection.auth?.let { json.encodeToString(it) },
                variables_json = encodeVariables(collection.variables),
                updated_at = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun deleteCollection(collectionId: String) {
        withContext(Dispatchers.IO) {
            // Explicit child deletion also works with drivers configured without foreign-key enforcement.
            database.transaction {
                queries.deleteItemsByCollectionId(collectionId)
                queries.deleteCollection(collectionId)
            }
        }
    }

    override suspend fun renameCollection(
        collectionId: String,
        newName: String,
    ) {
        withContext(Dispatchers.IO) {
            queries.renameCollection(newName, System.currentTimeMillis(), collectionId)
        }
    }

    override suspend fun addRequestToCollection(
        collectionId: String,
        request: HttpRequest,
        name: String,
        parentFolderId: String?,
    ): CollectionItem.Request =
        withContext(Dispatchers.IO) {
            val item = CollectionItem.Request(name = name, request = request)
            database.transaction {
                validateParent(collectionId, parentFolderId)
                insertItems(collectionId, listOf(item), parentFolderId, nextSiblingPosition(collectionId, parentFolderId))
                queries.touchCollection(System.currentTimeMillis(), collectionId)
            }
            item
        }

    override suspend fun addFolderToCollection(
        collectionId: String,
        name: String,
        description: String,
        parentFolderId: String?,
    ): CollectionItem.Folder =
        withContext(Dispatchers.IO) {
            val folder = CollectionItem.Folder(name = name, description = description)
            database.transaction {
                validateParent(collectionId, parentFolderId)
                insertItems(collectionId, listOf(folder), parentFolderId, nextSiblingPosition(collectionId, parentFolderId))
                queries.touchCollection(System.currentTimeMillis(), collectionId)
            }
            folder
        }

    override suspend fun moveCollectionItem(
        itemId: String,
        targetFolderId: String?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            database.transactionWithResult {
                val item = queries.selectCollectionItemById(itemId).executeAsOneOrNull() ?: return@transactionWithResult false
                if (item.parent_folder_id == targetFolderId) return@transactionWithResult false
                if (targetFolderId != null) {
                    val target =
                        queries.selectCollectionItemById(targetFolderId).executeAsOneOrNull()
                            ?: return@transactionWithResult false
                    if (target.collection_id != item.collection_id || target.type != "folder" ||
                        isSelfOrDescendant(targetFolderId, itemId)
                    ) {
                        return@transactionWithResult false
                    }
                }
                queries.moveCollectionItem(targetFolderId, nextSiblingPosition(item.collection_id, targetFolderId), itemId)
                queries.touchCollection(System.currentTimeMillis(), item.collection_id)
                true
            }
        }

    override suspend fun deleteCollectionItem(itemId: String) {
        withContext(Dispatchers.IO) {
            database.transaction {
                val item = queries.selectCollectionItemById(itemId).executeAsOneOrNull()

                fun deleteTree(id: String) {
                    queries.selectItemsByParentFolderId(id).executeAsList().forEach { deleteTree(it.id) }
                    queries.deleteCollectionItem(id)
                }
                deleteTree(itemId)
                item?.let { queries.touchCollection(System.currentTimeMillis(), it.collection_id) }
            }
        }
    }

    override suspend fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) {
        withContext(Dispatchers.IO) {
            database.transaction {
                val item = queries.selectCollectionItemById(itemId).executeAsOneOrNull()
                queries.renameCollectionItem(newName, itemId)
                item?.let { queries.touchCollection(System.currentTimeMillis(), it.collection_id) }
            }
        }
    }

    override suspend fun importCollection(collection: RequestCollection): RequestCollection =
        withContext(Dispatchers.IO) {
            database.transaction {
                queries.insertCollection(
                    id = collection.id,
                    name = collection.name,
                    description = collection.description,
                    auth_json = collection.auth?.let { json.encodeToString(it) },
                    variables_json = encodeVariables(collection.variables),
                    created_at = collection.createdAt,
                    updated_at = collection.updatedAt,
                )
                insertItems(collection.id, collection.items, null)
            }
            collection
        }

    private fun encodeVariables(variables: List<Variable>): String? = variables.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    private fun insertItems(
        collectionId: String,
        items: List<CollectionItem>,
        parentId: String?,
        firstPosition: Long = 0,
    ) {
        items.forEachIndexed { index, item ->
            queries.insertCollectionItem(
                id = item.id,
                collection_id = collectionId,
                parent_folder_id = parentId,
                type = if (item is CollectionItem.Folder) "folder" else "request",
                name = item.name,
                description = (item as? CollectionItem.Folder)?.description.orEmpty(),
                request_json = (item as? CollectionItem.Request)?.request?.let { json.encodeToString(it) },
                position = firstPosition + index,
            )
            if (item is CollectionItem.Folder) insertItems(collectionId, item.items, item.id)
        }
    }

    private fun validateParent(
        collectionId: String,
        parentId: String?,
    ) {
        require(queries.selectCollectionById(collectionId).executeAsOneOrNull() != null) { "Collection does not exist" }
        if (parentId != null) {
            val parent = queries.selectCollectionItemById(parentId).executeAsOneOrNull()
            require(parent?.collection_id == collectionId && parent.type == "folder") { "Invalid parent folder" }
        }
    }

    private fun nextSiblingPosition(
        collectionId: String,
        parentFolderId: String?,
    ): Long {
        val siblings =
            if (parentFolderId == null) {
                queries.selectRootItemsByCollectionId(collectionId)
            } else {
                queries.selectItemsByParentFolderId(parentFolderId)
            }
        return (siblings.executeAsList().maxOfOrNull { it.position } ?: -1L) + 1L
    }

    private fun isSelfOrDescendant(
        folderId: String,
        itemId: String,
    ): Boolean {
        var current: String? = folderId
        val visited = mutableSetOf<String>()
        while (current != null) {
            if (current == itemId || !visited.add(current)) return true
            current = queries.selectCollectionItemById(current).executeAsOneOrNull()?.parent_folder_id
        }
        return false
    }
}
