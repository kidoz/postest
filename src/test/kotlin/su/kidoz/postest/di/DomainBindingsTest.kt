package su.kidoz.postest.di

import io.ktor.client.HttpClient
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.repository.TestDatabaseFactory
import su.kidoz.postest.domain.port.*
import su.kidoz.postest.domain.usecase.*
import kotlin.test.Test
import kotlin.test.assertNotNull

class DomainBindingsTest {
    @Test fun `production modules bind domain contracts to adapters without opening the user database`() {
        val app =
            koinApplication {
                modules(
                    networkModule,
                    repositoryModule,
                    useCaseModule,
                    module {
                        single<PostestDatabase> { TestDatabaseFactory.createInMemoryDatabase() }
                    },
                )
            }
        try {
            val koin = app.koin
            assertNotNull(koin.get<CollectionRepository>())
            assertNotNull(koin.get<EnvironmentRepository>())
            assertNotNull(koin.get<HistoryRepository>())
            assertNotNull(koin.get<DraftSessionStore>())
            assertNotNull(koin.get<CollectionFiles>())
            assertNotNull(koin.get<ExecuteRequestUseCase>())
            assertNotNull(koin.get<ManageCollectionsUseCase>())
            assertNotNull(koin.get<ManageEnvironmentsUseCase>())
            assertNotNull(koin.get<ManageHistoryUseCase>())
            assertNotNull(koin.get<ManageSessionUseCase>())
            assertNotNull(koin.get<ImportCollectionUseCase>())
            assertNotNull(koin.get<ExportCollectionUseCase>())
        } finally {
            app.koin.get<HttpClient>().close()
            app.close()
        }
    }
}
