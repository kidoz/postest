package su.kidoz.postest.domain.port

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.domain.model.*

interface CollectionRepository {
    val collections: Flow<List<RequestCollection>>

    suspend fun loadCollections(): List<RequestCollection>

    suspend fun createCollection(
        name: String,
        description: String = "",
    ): RequestCollection

    suspend fun updateCollection(collection: RequestCollection)

    suspend fun deleteCollection(collectionId: String)

    suspend fun renameCollection(
        collectionId: String,
        newName: String,
    )

    suspend fun addRequestToCollection(
        collectionId: String,
        request: HttpRequest,
        name: String,
        parentFolderId: String? = null,
    ): CollectionItem.Request

    suspend fun addFolderToCollection(
        collectionId: String,
        name: String,
        description: String = "",
        parentFolderId: String? = null,
    ): CollectionItem.Folder

    suspend fun moveCollectionItem(
        itemId: String,
        targetFolderId: String?,
    ): Boolean

    suspend fun deleteCollectionItem(itemId: String)

    suspend fun renameCollectionItem(
        itemId: String,
        newName: String,
    )

    suspend fun importCollection(collection: RequestCollection): RequestCollection
}
