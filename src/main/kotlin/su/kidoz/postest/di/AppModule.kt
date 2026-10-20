package su.kidoz.postest.di

import app.cash.sqldelight.db.SqlDriver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import org.koin.dsl.module
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.export.PostmanExporter
import su.kidoz.postest.data.files.LocalCollectionFiles
import su.kidoz.postest.data.http.HttpClientFactory
import su.kidoz.postest.data.http.KtorWebSocketTransport
import su.kidoz.postest.data.http.RequestExecutor
import su.kidoz.postest.data.import.OpenAPICollectionDecoder
import su.kidoz.postest.data.import.OpenAPIImporter
import su.kidoz.postest.data.import.PostmanCollectionDecoder
import su.kidoz.postest.data.import.PostmanCollectionEncoder
import su.kidoz.postest.data.import.PostmanImporter
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.data.repository.HistoryRepository
import su.kidoz.postest.data.repository.SessionRepository
import su.kidoz.postest.data.security.PlatformKeyStore
import su.kidoz.postest.data.security.ProtectedStorage
import su.kidoz.postest.data.security.RecordCodec
import su.kidoz.postest.data.security.StorageBootstrap
import su.kidoz.postest.domain.port.CollectionFiles
import su.kidoz.postest.domain.port.DraftSessionStore
import su.kidoz.postest.domain.port.RequestTransport
import su.kidoz.postest.domain.port.WebSocketTransport
import su.kidoz.postest.domain.usecase.ConnectWebSocketUseCase
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.domain.usecase.ManageSessionUseCase
import su.kidoz.postest.util.VariableResolver
import su.kidoz.postest.viewmodel.MainViewModel
import java.io.File
import su.kidoz.postest.domain.port.CollectionRepository as CollectionRepositoryPort
import su.kidoz.postest.domain.port.EnvironmentRepository as EnvironmentRepositoryPort
import su.kidoz.postest.domain.port.HistoryRepository as HistoryRepositoryPort

private val logger = KotlinLogging.logger {}

private val httpLoggingEnabled: Boolean by lazy {
    val envFlag = System.getenv("POSTEST_HTTP_LOGGING")?.equals("true", ignoreCase = true) == true
    val sysPropFlag = System.getProperty("postest.httpLogging")?.equals("true", ignoreCase = true) == true
    envFlag || sysPropFlag
}

val databaseModule =
    module {
        single { StorageBootstrap(File(System.getProperty("user.home"), ".postest").toPath(), PlatformKeyStore()) }
        single<ProtectedStorage> { checkNotNull(get<StorageBootstrap>().current) { "Protected storage has not been unlocked" } }
        single<SqlDriver> { get<ProtectedStorage>().driver }
        single<RecordCodec> { get<ProtectedStorage>().codec }

        single<PostestDatabase> {
            PostestDatabase(get())
        }
    }

private fun createHttpClient(): HttpClient {
    if (httpLoggingEnabled) {
        logger.info { "HTTP logging enabled (headers only)" }
    }
    return HttpClientFactory.create(enableLogging = httpLoggingEnabled)
}

val networkModule =
    module {
        single<HttpClient> { createHttpClient() }

        single<VariableResolver> {
            VariableResolver()
        }

        single<RequestTransport> {
            RequestExecutor(get(), get())
        }

        single<WebSocketTransport> {
            KtorWebSocketTransport(get(), get())
        }
    }

val repositoryModule =
    module {
        single<CollectionFiles> { LocalCollectionFiles() }
        single<DraftSessionStore> { SessionRepository(get(), get()) }
        single<CollectionRepositoryPort> {
            CollectionRepository(get(), get())
        }

        single<EnvironmentRepositoryPort> {
            EnvironmentRepository(get(), get())
        }

        single<HistoryRepositoryPort> {
            HistoryRepository(get(), get())
        }
    }

val useCaseModule =
    module {
        single<ManageSessionUseCase> { ManageSessionUseCase(get()) }
        single<ExecuteRequestUseCase> {
            ExecuteRequestUseCase(get(), get())
        }

        single<ConnectWebSocketUseCase> {
            ConnectWebSocketUseCase(get())
        }

        single<ManageCollectionsUseCase> {
            ManageCollectionsUseCase(get())
        }

        single<ManageEnvironmentsUseCase> {
            ManageEnvironmentsUseCase(get())
        }

        single<ManageHistoryUseCase> {
            ManageHistoryUseCase(get())
        }

        single<PostmanImporter> {
            PostmanImporter()
        }

        single<OpenAPIImporter> {
            OpenAPIImporter()
        }

        single<PostmanExporter> {
            PostmanExporter()
        }

        single<ImportCollectionUseCase> {
            ImportCollectionUseCase(
                get(),
                PostmanCollectionDecoder(get()),
                OpenAPICollectionDecoder(get()),
                get(),
            )
        }

        single<ExportCollectionUseCase> {
            ExportCollectionUseCase(PostmanCollectionEncoder(get()), get())
        }
    }

val viewModelModule =
    module {
        single<MainViewModel> {
            MainViewModel(get(), get(), get(), get(), get(), get(), get(), get())
        }
    }

val appModules =
    listOf(
        databaseModule,
        networkModule,
        repositoryModule,
        useCaseModule,
        viewModelModule,
    )
