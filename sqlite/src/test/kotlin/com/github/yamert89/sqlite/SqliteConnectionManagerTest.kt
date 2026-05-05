package com.github.yamert89.sqlite

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.DriverManager

class SqliteConnectionManagerTest {
    @Test
    fun `jdbcUrl should use sqlite protocol in read only mode`() {
        val url = SqliteConnectionManager.jdbcUrl("/tmp/app.db")

        assertTrue(url.startsWith("jdbc:sqlite:"))
        assertTrue(url.contains("mode=ro"))
    }

    @Test
    fun `getConnection should throw for missing file`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                SqliteConnectionManager.getConnection("/tmp/missing-${System.nanoTime()}.db")
            }

        assertNotNull(exception.message)
    }

    @Test
    fun `getConnection should open existing sqlite file`() {
        val dbFile = Files.createTempFile("drift-locator-", ".db")
        DriverManager.getConnection("jdbc:sqlite:${dbFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT)")
        }

        SqliteConnectionManager.getConnection(dbFile.toString()).use { connection ->
            assertTrue(connection.isValid(5))
        }
    }
}
