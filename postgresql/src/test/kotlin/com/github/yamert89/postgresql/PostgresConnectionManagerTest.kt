package com.github.yamert89.postgresql

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * Unit tests for PostgresConnectionManager.
 * Note: These tests verify exception handling without actual database connections.
 * Full connection tests are in PostgresSchemaComparatorIntegrationTest with Testcontainers.
 */
class PostgresConnectionManagerTest {
    @Test
    fun `getConnection should throw SQLException for invalid host`() {
        val exception =
            assertThrows(SQLException::class.java) {
                PostgresConnectionManager.getConnection(
                    host = "invalid_host_12345",
                    port = 5432,
                    database = "test",
                    username = "test",
                    password = "test",
                )
            }

        assertNotNull(exception.message)
    }

    @Test
    fun `getConnection should throw SQLException for unreachable port`() {
        val exception =
            assertThrows(SQLException::class.java) {
                val invalidPort = 1
                PostgresConnectionManager.getConnection(
                    host = "localhost",
                    port = invalidPort,
                    database = "test",
                    username = "test",
                    password = "test",
                )
            }

        assertNotNull(exception.message)
    }

    @Test
    fun `getConnection should handle null password`() {
        // This will fail to connect but should not throw NullPointerException
        val exception =
            assertThrows(SQLException::class.java) {
                PostgresConnectionManager.getConnection(
                    host = "localhost",
                    port = 1,
                    database = "test",
                    username = "test",
                    password = null,
                )
            }

        assertNotNull(exception)
    }

    @Test
    fun `getConnection should handle empty password`() {
        // This will fail to connect but should not throw NullPointerException
        val exception =
            assertThrows(SQLException::class.java) {
                PostgresConnectionManager.getConnection(
                    host = "localhost",
                    port = 1,
                    database = "test",
                    username = "test",
                    password = "",
                )
            }

        assertNotNull(exception)
    }
}
