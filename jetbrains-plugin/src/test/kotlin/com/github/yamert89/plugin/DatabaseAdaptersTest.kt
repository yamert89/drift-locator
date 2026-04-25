package com.github.yamert89.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DatabaseAdaptersTest {
    @Test
    fun `registry should return PostgreSQL and MySQL defaults`() {
        val postgres = DatabaseAdapters.defaults(DatabaseType.POSTGRESQL)
        val mysql = DatabaseAdapters.defaults(DatabaseType.MYSQL)

        assertEquals(5432, postgres.port)
        assertEquals("public", postgres.schema)
        assertEquals(3306, mysql.port)
        assertEquals("mysql", mysql.schema)
    }

    @Test
    fun `registry should return adapters for both database types`() {
        assertEquals(DatabaseType.POSTGRESQL, DatabaseAdapters.forType(DatabaseType.POSTGRESQL).type)
        assertEquals(DatabaseType.MYSQL, DatabaseAdapters.forType(DatabaseType.MYSQL).type)
    }

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
        )
}
