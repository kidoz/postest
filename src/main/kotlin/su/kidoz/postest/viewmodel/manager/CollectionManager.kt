package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater

/**
 * Manager for collection CRUD operations.
 * Handles: collection loading, creation, deletion, renaming, item management.
 */
class CollectionManager(
    private val manageCollectionsUseCase: ManageCollectionsUseCase,
    private val stateUpdater: StateUpdater<AppState>,
    private val sideEffectEmitter: SideEffectEmitter,
    private val stateProvider: () -> AppState,
    private val scope: CoroutineScope,
) {
    /**
     * Initializes collections by subscribing to the collections flow.
     */
    fun initialize() {
        scope.launch {
            manageCollectionsUseCase.collections.collect { collections ->
                stateUpdater.update { it.copy(collections = collections) }
            }
        }

        scope.launch {
            manageCollectionsUseCase.loadCollections()
        }
    }

    /**
     * Shows the new collection dialog.
     */
    fun showNewCollectionDialog() {
        stateUpdater.update { it.copy(showNewCollectionDialog = true) }
    }

    /**
     * Hides the new collection dialog.
     */
    fun hideNewCollectionDialog() {
        stateUpdater.update { it.copy(showNewCollectionDialog = false) }
    }

    /**
     * Creates a new collection.
     */
    fun createCollection(name: String) {
        scope.launch {
            manageCollectionsUseCase.createCollection(name)
            stateUpdater.update { it.copy(showNewCollectionDialog = false) }
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Collection '$name' created"))
        }
    }

    /**
     * Deletes a collection.
     */
    fun deleteCollection(collectionId: String) {
        scope.launch {
            val collectionName =
                stateProvider()
                    .collections
                    .find { it.id == collectionId }
                    ?.name ?: ""
            manageCollectionsUseCase.deleteCollection(collectionId)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Collection '$collectionName' deleted"))
        }
    }

    /**
     * Renames a collection.
     */
    fun renameCollection(
        collectionId: String,
        newName: String,
    ) {
        scope.launch {
            manageCollectionsUseCase.renameCollection(collectionId, newName)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Collection renamed to '$newName'"))
        }
    }

    /**
     * Adds the current request to a collection.
     */
    fun addRequestToCollection(
        collectionId: String,
        request: HttpRequest,
        name: String,
    ) {
        scope.launch {
            val requestName = name.ifBlank { "New Request" }
            manageCollectionsUseCase.addRequestToCollection(
                collectionId = collectionId,
                request = request,
                name = requestName,
            )
            val collectionName =
                stateProvider()
                    .collections
                    .find { it.id == collectionId }
                    ?.name ?: ""
            sideEffectEmitter.emit(
                AppSideEffect.ShowToast("Request '$requestName' added to '$collectionName'"),
            )
        }
    }

    /**
     * Deletes a collection item (request or folder).
     */
    fun deleteCollectionItem(itemId: String) {
        scope.launch {
            manageCollectionsUseCase.deleteCollectionItem(itemId)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Item deleted"))
        }
    }

    /**
     * Renames a collection item (request or folder).
     */
    fun renameCollectionItem(
        itemId: String,
        newName: String,
    ) {
        scope.launch {
            manageCollectionsUseCase.renameCollectionItem(itemId, newName)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Item renamed to '$newName'"))
        }
    }
}
