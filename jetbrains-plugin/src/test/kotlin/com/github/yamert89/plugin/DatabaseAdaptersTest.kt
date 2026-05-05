package com.github.yamert89.plugin

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DatabaseAdaptersTest {
    @Test
    fun `cross database comparison should be rejected`() {
        val postgres = connection("postgres", DatabaseType.POSTGRESQL)
        val mysql = connection("mysql", DatabaseType.MYSQL)

        val error = crossDatabaseComparisonError(postgres, mysql)

        assertNotNull(error)
    }

    @Test
    fun `same database comparison should be allowed`() {
        val source = connection("source", DatabaseType.MYSQL)
        val target = connection("target", DatabaseType.MYSQL)

        assertNull(crossDatabaseComparisonError(source, target))
    }

    @Test
    fun `SQLite adapter should be resolved`() {
        val adapter = DatabaseAdapters.forType(DatabaseType.SQLITE)

        assertSame(DatabaseType.SQLITE, adapter.type)
    }

    private fun connection(name: String, type: DatabaseType): DatabaseConnection =
        DatabaseConnection(
            id = name,
            name = name,
            host = "localhost",
            port = DatabaseAdapters.defaults(type).port,
            database = DatabaseAdapters.defaults(type).database,
            databaseType = type,
            username = DatabaseAdapters.defaults(type).username,
            schema = DatabaseAdapters.defaults(type).schema,
            filePath = if (type == DatabaseType.SQLITE) "/tmp/test.db" else "",
            savePassword = type != DatabaseType.SQLITE,
        )
}
