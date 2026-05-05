package com.github.yamert89.sqlite

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager

class SqliteConnectionTesterTest {
    @Test
    fun `testConnection should fail for missing file`() {
        val result = SqliteConnectionTester.testConnection("/tmp/unknown-${System.nanoTime()}.db")

        assertTrue(result.isFailure)
    }

    @Test
    fun `testConnection should succeed for existing database file`() {
        val dbFile = Files.createTempFile("drift-locator-", ".db")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().execute("CREATE TABLE sample (id INTEGER PRIMARY KEY)")
        }

        val result = SqliteConnectionTester.testConnection(dbFile.toString())

        assertTrue(result.getOrThrow())
    }
}
