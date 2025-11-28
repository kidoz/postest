package su.kidoz.postest.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.postest.data.http.RequestExecutor
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.domain.model.Environment
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.HttpRequest
import su.kidoz.postest.domain.model.HttpResponse
import su.kidoz.postest.domain.model.ResponseTime
import su.kidoz.postest.domain.model.Variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecuteRequestUseCaseTest {
    private val requestExecutor: RequestExecutor = mockk()
    private val environmentRepository: EnvironmentRepository = mockk()
    private val useCase = ExecuteRequestUseCase(requestExecutor, environmentRepository)

    @Test
    fun `invoke should execute request with active environment`() =
        runTest {
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "{{baseUrl}}/users",
                )
            val environment =
                Environment(
                    name = "Production",
                    variables = listOf(Variable(key = "baseUrl", value = "https://api.example.com")),
                    isActive = true,
                )
            val response = createSuccessResponse()

            coEvery { environmentRepository.getActiveEnvironment() } returns environment
            coEvery { requestExecutor.execute(request, environment) } returns Result.success(response)

            val result = useCase(request)

            assertTrue(result.isSuccess)
            assertEquals(200, result.getOrNull()?.statusCode)
            coVerify { requestExecutor.execute(request, environment) }
        }

    @Test
    fun `invoke should execute request without environment when none active`() =
        runTest {
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )
            val response = createSuccessResponse()

            coEvery { environmentRepository.getActiveEnvironment() } returns null
            coEvery { requestExecutor.execute(request, null) } returns Result.success(response)

            val result = useCase(request)

            assertTrue(result.isSuccess)
            coVerify { requestExecutor.execute(request, null) }
        }

    @Test
    fun `invoke should return failure when request fails`() =
        runTest {
            val request =
                HttpRequest(
                    method = HttpMethod.GET,
                    url = "https://api.example.com/users",
                )
            val exception = RuntimeException("Network error")

            coEvery { environmentRepository.getActiveEnvironment() } returns null
            coEvery { requestExecutor.execute(request, null) } returns Result.failure(exception)

            val result = useCase(request)

            assertTrue(result.isFailure)
            assertEquals("Network error", result.exceptionOrNull()?.message)
        }

    @Test
    fun `invoke should pass environment to executor`() =
        runTest {
            val request = HttpRequest(url = "https://api.example.com")
            val environment =
                Environment(
                    name = "Dev",
                    variables =
                        listOf(
                            Variable(key = "token", value = "abc123"),
                        ),
                )
            val response = createSuccessResponse()

            coEvery { environmentRepository.getActiveEnvironment() } returns environment
            coEvery { requestExecutor.execute(request, environment) } returns Result.success(response)

            useCase(request)

            coVerify { requestExecutor.execute(request, environment) }
        }

    @Test
    fun `invoke should handle POST request`() =
        runTest {
            val request =
                HttpRequest(
                    method = HttpMethod.POST,
                    url = "https://api.example.com/users",
                )
            val response =
                HttpResponse(
                    statusCode = 201,
                    statusText = "Created",
                    headers = emptyMap(),
                    body = """{"id": 1}""",
                    contentType = "application/json",
                    size = 10,
                    time = ResponseTime(total = 100),
                )

            coEvery { environmentRepository.getActiveEnvironment() } returns null
            coEvery { requestExecutor.execute(request, null) } returns Result.success(response)

            val result = useCase(request)

            assertTrue(result.isSuccess)
            assertEquals(201, result.getOrNull()?.statusCode)
        }

    private fun createSuccessResponse() =
        HttpResponse(
            statusCode = 200,
            statusText = "OK",
            headers = emptyMap(),
            body = "{}",
            contentType = "application/json",
            size = 2,
            time = ResponseTime(total = 50),
        )
}
