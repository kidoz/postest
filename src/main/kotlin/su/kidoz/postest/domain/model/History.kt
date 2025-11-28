package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val request: HttpRequest,
    val response: HttpResponse?,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long,
)
