package su.kidoz.postest.di

import app.cash.sqldelight.db.QueryResult
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
import su.kidoz.postest.data.repository.SessionRepository
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
    if (hasTable(driver, "Collection")) {
        logger.debug { "Database schema already exists" }
        runMigrations(driver)
    } else {
        PostestDatabase.Schema.create(driver)
        logger.info { "Database schema created successfully" }
    }
    configureDriver(driver)
    return driver
}

private fun hasTable(
    driver: SqlDriver,
    table: String,
): Boolean =
    driver
        .executeQuery(
            identifier = null,
            sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name='$table'",
            mapper = { cursor -> QueryResult.Value(cursor.next().value) },
            parameters = 0,
        ).value

private fun hasColumn(
    driver: SqlDriver,
    table: String,
    column: String,
): Boolean =
    driver
        .executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            mapper = { cursor ->
                var found = false
                while (cursor.next().value) {
                    if (cursor.getString(1) == column) {
                        found = true
                    }
                }
                QueryResult.Value(found)
            },
            parameters = 0,
        ).value

private fun configureDriver(driver: SqlDriver) {
    // Enable FK enforcement so ON DELETE CASCADE clauses in the schema actually work
    driver.execute(null, "PRAGMA foreign_keys = ON", 0)

    // Indexes for frequent lookups; IF NOT EXISTS keeps them idempotent for fresh and existing databases
    driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_collection_item_collection_id ON CollectionItem(collection_id)", 0)
    driver.execute(
        null,
        "CREATE INDEX IF NOT EXISTS idx_collection_item_parent_folder_id ON CollectionItem(parent_folder_id)",
        0,
    )
    driver.execute(
        null,
        "CREATE INDEX IF NOT EXISTS idx_environment_variable_environment_id ON EnvironmentVariable(environment_id)",
        0,
    )
    driver.execute(null, "CREATE INDEX IF NOT EXISTS idx_history_entry_timestamp ON HistoryEntry(timestamp)", 0)
}

private fun runMigrations(driver: SqlDriver) {
    migrateWorkspaceSession(driver)

    if (!hasColumn(driver, "HistoryEntry", "error_message")) {
        driver.execute(null, "ALTER TABLE HistoryEntry ADD COLUMN error_message TEXT", 0)
        logger.info { "Migration: Added error_message column to HistoryEntry table" }
    }

    if (!hasColumn(driver, "Collection", "variables_json")) {
        driver.execute(null, "ALTER TABLE Collection ADD COLUMN variables_json TEXT", 0)
        logger.info { "Migration: Added variables_json column to Collection table" }
    }
}

internal fun migrateWorkspaceSession(driver: SqlDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS WorkspaceSession (
            id TEXT NOT NULL PRIMARY KEY CHECK (id IN ('current', 'backup')),
            snapshot_json TEXT NOT NULL
        )
        """.trimIndent(),
        0,
    )
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
    }

val repositoryModule =
    module {
        single<SessionRepository> { SessionRepository(get()) }
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
        single<ManageSessionUseCase> { ManageSessionUseCase(get()) }
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
            MainViewModel(get(), get(), get(), get(), get(), get(), get())
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
