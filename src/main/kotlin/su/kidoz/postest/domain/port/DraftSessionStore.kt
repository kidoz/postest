package su.kidoz.postest.domain.port

import kotlinx.coroutines.flow.Flow
import su.kidoz.postest.domain.model.*

interface DraftSessionStore {
    suspend fun load(): DraftSession?

    suspend fun save(session: DraftSession)
}
