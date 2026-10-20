package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter

/** Coordinates collection commands; database observations provide the resulting data. */
class CollectionManager(
    private val useCase: ManageCollectionsUseCase,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
    private val collectionCreated: () -> Unit,
) {
    val collections = useCase.collections

    fun createCollection(name: String) =
        launch {
            useCase.createCollection(name)
            collectionCreated()
            effects.emit(AppSideEffect.ShowToast("Collection '$name' created"))
        }

    fun deleteCollection(id: String) = launch { useCase.deleteCollection(id) }

    fun renameCollection(
        id: String,
        name: String,
    ) = launch { useCase.renameCollection(id, name) }

    fun addRequestToCollection(
        id: String,
        request: HttpRequest,
        name: String,
    ) = launch {
        useCase.addRequestToCollection(id, request, name.ifBlank { "New Request" })
        effects.emit(AppSideEffect.ShowToast("Request added to collection"))
    }

    fun addFolderToCollection(
        id: String,
        name: String,
        parentId: String?,
    ) = launch {
        useCase.addFolderToCollection(id, name.ifBlank { "New Folder" }, parentFolderId = parentId)
    }

    fun moveCollectionItem(
        id: String,
        parentId: String?,
    ) = launch { useCase.moveCollectionItem(id, parentId) }

    fun deleteCollectionItem(id: String) = launch { useCase.deleteCollectionItem(id) }

    fun renameCollectionItem(
        id: String,
        name: String,
    ) = launch { useCase.renameCollectionItem(id, name) }

    private fun launch(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                effects.emit(AppSideEffect.ShowError("Collection operation failed"))
            }
        }
    }
}
