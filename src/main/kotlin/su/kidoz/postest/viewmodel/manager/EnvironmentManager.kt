package su.kidoz.postest.viewmodel.manager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.viewmodel.AppSideEffect
import su.kidoz.postest.viewmodel.SideEffectEmitter

/** Serializes environment edits and captures execution context after preceding selection commands commit. */
class EnvironmentManager(
    private val useCase: ManageEnvironmentsUseCase,
    private val effects: SideEffectEmitter,
    private val scope: CoroutineScope,
) {
    val snapshot = useCase.snapshot
    private val operations = Mutex()

    fun selectEnvironment(id: String?) = launch { useCase.setActiveEnvironment(id) }

    fun saveEnvironment(environment: Environment) =
        launch {
            val exists = useCase.loadEnvironments().any { it.id == environment.id }
            if (exists) useCase.updateEnvironment(environment) else useCase.saveEnvironment(environment)
            effects.emit(AppSideEffect.ShowToast("Environment '${environment.name}' saved"))
        }

    fun createEnvironment(name: String) = launch { useCase.createEnvironment(name) }

    fun deleteEnvironment(id: String) = launch { useCase.deleteEnvironment(id) }

    fun captureEnvironment(action: (Environment?) -> Unit) = launch { action(useCase.getActiveEnvironment()) }

    private fun launch(action: suspend () -> Unit) {
        scope.launch {
            operations.withLock {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    effects.emit(AppSideEffect.ShowError("Environment operation failed"))
                }
            }
        }
    }
}
