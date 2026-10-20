package su.kidoz.postest.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.domain.model.*
import su.kidoz.postest.domain.port.EnvironmentRepository
import su.kidoz.postest.domain.port.RequestTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExecuteRequestUseCaseTest {
    private val transport = mockk<RequestTransport>()
    private val environments = mockk<EnvironmentRepository>()
    private val useCase = ExecuteRequestUseCase(transport, environments)
    private val request = HttpRequest(url = "{{baseUrl}}/users")
    private val response = HttpResponse(200, "OK", emptyMap(), "{}", "application/json", 2, ResponseTime(50))

    @Test fun `invocation captures active environment and collection variables once`() =
        runTest {
            val env = Environment(name = "Dev", variables = listOf(Variable(key = "baseUrl", value = "https://example.test")))
            val variables = listOf(Variable(key = "version", value = "1"))
            coEvery { environments.getActiveEnvironment() } returns env
            coEvery { transport.executeRequest(any(), any()) } returns RequestOutcome.Success(response)
            assertEquals(response, useCase(request, variables).getOrThrow())
            coVerify(exactly = 1) { environments.getActiveEnvironment() }
            coVerify { transport.executeRequest(request, ExecutionContext(env, variables)) }
        }

    @Test fun `explicit context cannot change when active environment changes`() =
        runTest {
            val captured = ExecutionContext(Environment(name = "Captured"))
            coEvery { transport.executeRequest(request, captured) } returns RequestOutcome.Success(response)
            assertEquals(response, useCase.execute(request, captured).getOrThrow())
            coVerify(exactly = 0) { environments.getActiveEnvironment() }
        }

    @Test fun `no active environment is a valid context`() =
        runTest {
            coEvery { environments.getActiveEnvironment() } returns null
            coEvery { transport.executeRequest(request, ExecutionContext()) } returns RequestOutcome.Success(response)
            assertTrue(useCase(request).isSuccess)
        }

    @Test fun `expected failures expose only domain errors`() =
        runTest {
            coEvery { transport.executeRequest(any(), any()) } returns RequestOutcome.Failure(RequestFailure.TIMEOUT)
            val error = useCase.execute(request, ExecutionContext()).exceptionOrNull() as RequestFailureException
            assertEquals(RequestFailure.TIMEOUT, error.reason)
            assertEquals("Request timed out", error.message)
        }

    @Test fun `cancellation propagates across the transport boundary`() =
        runTest {
            coEvery { transport.executeRequest(any(), any()) } throws CancellationException("cancel")
            assertFailsWith<CancellationException> { useCase.execute(request, ExecutionContext()) }
        }
}
