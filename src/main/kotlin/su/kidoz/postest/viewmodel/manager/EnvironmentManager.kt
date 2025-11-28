package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.AppState
import su.kidoz.postest.viewmodel.SideEffectEmitter
import su.kidoz.postest.viewmodel.StateUpdater

/**
 * Manager for environment CRUD operations.
 * Handles: environment loading, creation, selection, deletion, updates.
 */
class EnvironmentManager(
    private val manageEnvironmentsUseCase: ManageEnvironmentsUseCase,
    private val stateUpdater: StateUpdater<AppState>,
    private val sideEffectEmitter: SideEffectEmitter,
    private val stateProvider: () -> AppState,
    private val scope: CoroutineScope,
) {
    /**
     * Initializes environments by subscribing to the environments flow.
     */
    fun initialize() {
        // Subscribe to environments updates
        scope.launch {
            manageEnvironmentsUseCase.environments.collect { environments ->
                stateUpdater.update { it.copy(environments = environments) }
            }
        }

        // Subscribe to active environment updates
        scope.launch {
            manageEnvironmentsUseCase.activeEnvironmentId.collect { activeId ->
                stateUpdater.update { it.copy(activeEnvironmentId = activeId) }
            }
        }

        scope.launch {
            manageEnvironmentsUseCase.loadEnvironments()
        }
    }

    /**
     * Shows the environment manager dialog.
     */
    fun showEnvironmentDialog() {
        stateUpdater.update { it.copy(showEnvironmentDialog = true) }
    }

    /**
     * Hides the environment manager dialog.
     */
    fun hideEnvironmentDialog() {
        stateUpdater.update { it.copy(showEnvironmentDialog = false) }
    }

    /**
     * Selects an environment as active.
     */
    fun selectEnvironment(environmentId: String?) {
        scope.launch {
            manageEnvironmentsUseCase.setActiveEnvironment(environmentId)
        }
    }

    /**
     * Saves an environment (creates if new, updates if exists).
     */
    fun saveEnvironment(environment: Environment) {
        scope.launch {
            val isNew = stateProvider().environments.none { it.id == environment.id }
            if (isNew) {
                manageEnvironmentsUseCase.saveEnvironment(environment)
                sideEffectEmitter.emit(
                    AppSideEffect.ShowToast("Environment '${environment.name}' created"),
                )
            } else {
                manageEnvironmentsUseCase.updateEnvironment(environment)
                sideEffectEmitter.emit(
                    AppSideEffect.ShowToast("Environment '${environment.name}' saved"),
                )
            }
        }
    }

    /**
     * Creates a new environment.
     */
    fun createEnvironment(name: String) {
        scope.launch {
            manageEnvironmentsUseCase.createEnvironment(name)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Environment '$name' created"))
        }
    }

    /**
     * Deletes an environment.
     */
    fun deleteEnvironment(environmentId: String) {
        scope.launch {
            val envName =
                stateProvider()
                    .environments
                    .find { it.id == environmentId }
                    ?.name ?: ""
            manageEnvironmentsUseCase.deleteEnvironment(environmentId)
            sideEffectEmitter.emit(AppSideEffect.ShowToast("Environment '$envName' deleted"))
        }
    }
}
