package su.kidoz.postest.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.data.security.ProtectedStorage
import su.kidoz.postest.data.security.RecordCipher
import su.kidoz.postest.data.security.StorageBootstrap
import su.kidoz.postest.data.security.StorageException
import su.kidoz.postest.data.security.StorageProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StorageControllerTest {
    @Test
    fun `locked storage stays blocked until retry succeeds`() =
        runTest {
            val storage = mockk<ProtectedStorage> { every { needsRecoveryAcknowledgement } returns false }
            val bootstrap = mockk<StorageBootstrap>()
            every { bootstrap.open(null) } throws StorageException(StorageProblem.KEY_STORE_UNAVAILABLE) andThen storage
            val controller = StorageController(bootstrap, this, StandardTestDispatcher(testScheduler))
            controller.unlock()
            advanceUntilIdle()
            assertEquals(StorageState.Blocked(StorageProblem.KEY_STORE_UNAVAILABLE), controller.state.value)
            controller.unlock()
            advanceUntilIdle()
            assertEquals(StorageState.Ready, controller.state.value)
        }

    @Test
    fun `new storage requires saving the recovery key before workspace startup`() =
        runTest {
            val cipher = RecordCipher("test-vault", ByteArray(32) { it.toByte() })
            val storage =
                mockk<ProtectedStorage> {
                    every { needsRecoveryAcknowledgement } returns true
                    every { this@mockk.cipher } returns cipher
                    every { acknowledgeRecoveryKey() } returns Unit
                }
            val bootstrap =
                mockk<StorageBootstrap> {
                    every { open(null) } returns storage
                    every { current } returns storage
                }
            val controller = StorageController(bootstrap, this, StandardTestDispatcher(testScheduler))
            controller.unlock()
            advanceUntilIdle()
            assertEquals(cipher.recoveryKey(), assertIs<StorageState.RecoverySetup>(controller.state.value).key)
            verify(exactly = 0) { storage.acknowledgeRecoveryKey() }
            controller.acknowledgeRecoveryKey()
            advanceUntilIdle()
            assertEquals(StorageState.Ready, controller.state.value)
            verify(exactly = 1) { storage.acknowledgeRecoveryKey() }
            cipher.close()
        }

    @Test
    fun `invalid recovery does not open a workspace`() =
        runTest {
            val bootstrap =
                mockk<StorageBootstrap> {
                    every { open("wrong") } throws StorageException(StorageProblem.INVALID_DATA)
                }
            val controller = StorageController(bootstrap, this, StandardTestDispatcher(testScheduler))
            controller.unlock("wrong")
            advanceUntilIdle()
            assertEquals(StorageState.Blocked(StorageProblem.INVALID_DATA), controller.state.value)
            verify(exactly = 0) { bootstrap.current }
        }
}
