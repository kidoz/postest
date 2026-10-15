package su.kidoz.postest.domain.usecase

import su.kidoz.postest.data.repository.SessionRepository
import su.kidoz.postest.domain.model.DraftSession

class ManageSessionUseCase(
    private val repository: SessionRepository,
) {
    suspend fun load(): DraftSession? = repository.load()

    suspend fun save(session: DraftSession) {
        repository.save(session)
    }
}
