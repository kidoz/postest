package su.kidoz.postest.viewmodel.manager

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Manager for collection import/export operations.
 * Handles: importing from files, exporting to Postman format.
 */
class ImportExportManager(
    private val importCollectionUseCase: ImportCollectionUseCase,
    private val exportCollectionUseCase: ExportCollectionUseCase,
    private val stateProvider: () -> AppState,
    private val sideEffectEmitter: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    /**
     * Imports a collection from a file.
     */
    fun importCollection(file: File) {
        scope.launch {
            logger.info { "Importing collection from file: ${file.absolutePath}" }
            val result = importCollectionUseCase.importFromFile(file)
            result.fold(
                onSuccess = { collection ->
                    sideEffectEmitter.emit(
                        AppSideEffect.ShowToast("Collection '${collection.name}' imported successfully"),
                    )
                },
                onFailure = { error ->
                    logger.error(error) { "Failed to import collection: ${error.message}" }
                    sideEffectEmitter.emit(
                        AppSideEffect.ShowError("Import failed: ${error.message}"),
                    )
                },
            )
        }
    }

    /**
     * Exports a collection to Postman format.
     */
    fun exportCollection(
        collectionId: String,
        file: File,
    ) {
        scope.launch {
            val collection = stateProvider().collections.find { it.id == collectionId }
            if (collection == null) {
                sideEffectEmitter.emit(AppSideEffect.ShowError("Collection not found"))
                return@launch
            }

            logger.info { "Exporting collection '${collection.name}' to: ${file.absolutePath}" }
            val result = exportCollectionUseCase.exportToPostman(collection, file)
            result.fold(
                onSuccess = {
                    sideEffectEmitter.emit(
                        AppSideEffect.ShowToast("Collection '${collection.name}' exported successfully"),
                    )
                },
                onFailure = { error ->
                    logger.error(error) { "Failed to export collection: ${error.message}" }
                    sideEffectEmitter.emit(
                        AppSideEffect.ShowError("Export failed: ${error.message}"),
                    )
                },
            )
        }
    }
}
