package su.kidoz.postest.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import su.kidoz.postest.data.db.PostestDatabase
import su.kidoz.postest.data.security.RecordCipher

object TestDatabaseFactory {
    fun cipher() = RecordCipher("test-vault", ByteArray(RecordCipher.KEY_BYTES) { it.toByte() })

    fun createInMemoryDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    fun createInMemoryDatabase(): PostestDatabase {
        val driver = createInMemoryDriver()
        PostestDatabase.Schema.create(driver)
        return PostestDatabase(driver)
    }
}
