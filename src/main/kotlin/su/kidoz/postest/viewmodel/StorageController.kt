package su.kidoz.postest.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.postest.data.security.StorageBootstrap
import su.kidoz.postest.data.security.StorageException
import su.kidoz.postest.data.security.StorageProblem

sealed interface StorageState {
    data object Opening : StorageState

    data class Blocked(
        val problem: StorageProblem,
    ) : StorageState

    class RecoverySetup(
        val key: String,
    ) : StorageState {
        override fun toString(): String = "RecoverySetup([redacted])"
    }

    data object Ready : StorageState
}

/** Startup gate: MainViewModel, repository observers and autosave do not exist until Ready. */
class StorageController(
    private val bootstrap: StorageBootstrap,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableState = MutableStateFlow<StorageState>(StorageState.Opening)
    val state = mutableState.asStateFlow()
    private val operation = Mutex()

    fun unlock(recoveryKey: String? = null) {
        scope.launch {
            operation.withLock {
                mutableState.value = StorageState.Opening
                try {
                    val storage = withContext(ioDispatcher) { bootstrap.open(recoveryKey) }
                    mutableState.value =
                        if (storage.needsRecoveryAcknowledgement) {
                            StorageState.RecoverySetup(storage.cipher.recoveryKey())
                        } else {
                            StorageState.Ready
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: StorageException) {
                    mutableState.value = StorageState.Blocked(error.problem)
                } catch (_: Exception) {
                    mutableState.value = StorageState.Blocked(StorageProblem.MIGRATION_FAILED)
                }
            }
        }
    }

    fun acknowledgeRecoveryKey() {
        scope.launch {
            operation.withLock {
                mutableState.value = StorageState.Opening
                try {
                    withContext(ioDispatcher) { checkNotNull(bootstrap.current).acknowledgeRecoveryKey() }
                    mutableState.value = StorageState.Ready
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = StorageState.Blocked(StorageProblem.MIGRATION_FAILED)
                }
            }
        }
    }

    suspend fun close() {
        operation.withLock { withContext(ioDispatcher) { bootstrap.close() } }
    }
}
