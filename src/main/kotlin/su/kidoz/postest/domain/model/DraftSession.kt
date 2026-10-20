package su.kidoz.postest.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DraftSession(
    val version: Int = 1,
    val tabs: List<DraftTab>,
    val activeTabId: String?,
)

@Serializable
data class DraftTab(
    val id: String,
    val request: HttpRequest,
    val isDirty: Boolean,
    val bodyDrafts: List<RequestBody> = emptyList(),
    val collectionItemId: String? = null,
    val response: HttpResponse? = null,
    val error: String? = null,
)
