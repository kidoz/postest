package su.kidoz.postest.domain.usecase

import su.kidoz.postest.domain.model.DraftSession
import su.kidoz.postest.domain.port.DraftSessionStore

class ManageSessionUseCase(
    private val repository: DraftSessionStore,
) {
    suspend fun load(): DraftSession? = repository.load()

    suspend fun save(session: DraftSession) {
        repository.save(session)
    }
}
