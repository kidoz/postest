package su.kidoz.postest.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import su.kidoz.postest.data.db.PostestDatabase

object TestDatabaseFactory {
    fun createInMemoryDatabase(): PostestDatabase {
        val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PostestDatabase.Schema.create(driver)
        return PostestDatabase(driver)
    }
}
