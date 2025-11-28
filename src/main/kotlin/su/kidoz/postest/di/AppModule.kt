package su.kidoz.postest.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import org.koin.dsl.module
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.export.PostmanExporter
import su.kidoz.postest.data.http.HttpClientFactory
import su.kidoz.postest.data.http.RequestExecutor
import su.kidoz.postest.data.import.OpenAPIImporter
import su.kidoz.postest.data.import.PostmanImporter
import su.kidoz.postest.data.repository.CollectionRepository
import su.kidoz.postest.data.repository.EnvironmentRepository
import su.kidoz.postest.data.repository.HistoryRepository
import su.kidoz.postest.domain.usecase.ExecuteRequestUseCase
import su.kidoz.postest.domain.usecase.ExportCollectionUseCase
import su.kidoz.postest.domain.usecase.ImportCollectionUseCase
import su.kidoz.postest.domain.usecase.ManageCollectionsUseCase
import su.kidoz.postest.domain.usecase.ManageEnvironmentsUseCase
import su.kidoz.postest.domain.usecase.ManageHistoryUseCase
import su.kidoz.postest.security.SecretStore
import su.kidoz.postest.security.SecretStoreFactory
import su.kidoz.postest.util.VariableResolver
import su.kidoz.postest.viewmodel.MainViewModel
import java.io.File

private val logger = KotlinLogging.logger {}

private val httpLoggingEnabled: Boolean by lazy {
    val envFlag = System.getenv("POSTEST_HTTP_LOGGING")?.equals("true", ignoreCase = true) == true
    val sysPropFlag = System.getProperty("postest.httpLogging")?.equals("true", ignoreCase = true) == true
    envFlag || sysPropFlag
}

private fun createDatabaseDriver(): SqlDriver {
    val dbPath = File(System.getProperty("user.home"), ".postest/postest.db")
    dbPath.parentFile?.mkdirs()
    logger.info { "Initializing database at: ${dbPath.absolutePath}" }
    val driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")
    try {
        PostestDatabase.Schema.create(driver)
        logger.info { "Database schema created successfully" }
    } catch (e: Exception) {
        // SQLite throws "table already exists" when schema is already created
        val message = e.message ?: ""
        if (message.contains("already exists", ignoreCase = true)) {
            logger.debug { "Database schema already exists" }
            // Run migrations for existing databases
            runMigrations(driver)
        } else {
            // Re-throw unexpected errors (permission, disk space, corruption, etc.)
            logger.error(e) { "Failed to initialize database: ${e.message}" }
            throw e
        }
    }
    return driver
}

private fun runMigrations(driver: SqlDriver) {
    // Migration 1: Add error_message column to HistoryEntry table
    try {
        driver.execute(null, "ALTER TABLE HistoryEntry ADD COLUMN error_message TEXT", 0)
        logger.info { "Migration: Added error_message column to HistoryEntry table" }
    } catch (e: Exception) {
        // Column already exists or other error - ignore if it's a duplicate column error
        val message = e.message ?: ""
        if (!message.contains("duplicate column", ignoreCase = true)) {
            logger.debug { "Migration error_message column: ${e.message}" }
        }
    }
}

val databaseModule =
    module {
        single<SqlDriver> { createDatabaseDriver() }

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

        single<RequestExecutor> {
            RequestExecutor(get(), get())
        }

        single<SecretStore> {
            SecretStoreFactory.create()
        }
    }

val repositoryModule =
    module {
        single<CollectionRepository> {
            CollectionRepository(get())
        }

        single<EnvironmentRepository> {
            EnvironmentRepository(get())
        }

        single<HistoryRepository> {
            HistoryRepository(get())
        }
    }

val useCaseModule =
    module {
        single<ExecuteRequestUseCase> {
            ExecuteRequestUseCase(get(), get())
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
            ImportCollectionUseCase(get(), get(), get())
        }

        single<ExportCollectionUseCase> {
            ExportCollectionUseCase(get())
        }
    }

val viewModelModule =
    module {
        single<MainViewModel> {
            MainViewModel(get(), get(), get(), get(), get(), get())
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
