package su.kidoz.postest.domain.usecase

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.domain.model.CollectionItem
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.RequestCollection

class ManageCollectionsUseCase(
    private val collectionRepository: CollectionRepository,
) {
    val collections: Flow<List<RequestCollection>> = collectionRepository.collections

    suspend fun loadCollections(): List<RequestCollection> = collectionRepository.loadCollections()

    suspend fun createCollection(
        name: String,
        description: String = "",
    ): RequestCollection = collectionRepository.createCollection(name, description)

    suspend fun updateCollection(collection: RequestCollection) {
        collectionRepository.updateCollection(collection)
    }

    suspend fun deleteCollection(collectionId: String) {
        collectionRepository.deleteCollection(collectionId)
    }

    suspend fun renameCollection(
        collectionId: String,
        newName: String,
    ) {
        collectionRepository.renameCollection(collectionId, newName)
    }

    suspend fun addRequestToCollection(
        collectionId: String,
        request: HttpRequest,
        name: String,
        parentFolderId: String? = null,
    ): CollectionItem.Request = collectionRepository.addRequestToCollection(collectionId, request, name, parentFolderId)

    suspend fun addFolderToCollection(
        collectionId: String,
        name: String,
        description: String = "",
        parentFolderId: String? = null,
    ): CollectionItem.Folder = collectionRepository.addFolderToCollection(collectionId, name, description, parentFolderId)

    suspend fun deleteCollectionItem(itemId: String) {
        collectionRepository.deleteCollectionItem(itemId)
    }

    suspend fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) {
        collectionRepository.renameCollectionItem(itemId, newName)
    }
}
