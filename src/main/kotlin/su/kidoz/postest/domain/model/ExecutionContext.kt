package su.kidoz.postest.domain.model

/** Immutable variable inputs captured once when a request is sent. */
data class ExecutionContext(
    val environment: Environment? = null,
    val collectionVariables: List<Variable> = emptyList(),
)

sealed interface RequestOutcome {
    data class Success(
        val response: HttpResponse,
    ) : RequestOutcome

    data class Failure(
        val reason: RequestFailure,
    ) : RequestOutcome
}

enum class RequestFailure(
    val description: String,
) {
    INVALID_REQUEST("Invalid request"),
    INVALID_CERTIFICATE_PIN("Certificate pin is invalid"),
    BLOCKED("Request blocked"),
    HOST_NOT_FOUND("Host not found"),
    CONNECTION_FAILED("Connection failed"),
    TIMEOUT("Request timed out"),
    TLS("TLS error"),
    NETWORK("Network error"),
}

class RequestFailureException(
    val reason: RequestFailure,
) : Exception(reason.description)
