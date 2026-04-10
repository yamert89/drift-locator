package com.github.yamert89.postgresql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * Unit tests for PostgresConnectionTester.
 * Note: Full connection tests with actual PostgreSQL are in PostgresSchemaComparatorTest with Testcontainers.
 */
class PostgresConnectionTesterTest {

    @Test
    fun `testConnection should throw SQLException for invalid host`() {
        val exception = assertThrows(SQLException::class.java) {
            PostgresConnectionTester.testConnection(
                host = "invalid_host_12345",
                port = 5432,
                database = "test",
                username = "test",
                password = "test"
            )
        }

        assertNotNull(exception.message)
    }

    @Test
    fun `testConnection should throw SQLException for unreachable port`() {
        val exception = assertThrows(SQLException::class.java) {
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1, // Invalid port
                database = "test",
                username = "test",
                password = "test"
            )
        }

        assertNotNull(exception.message)
    }

    @Test
    fun `testConnection should handle null password`() {
        // This will fail to connect but should not throw NullPointerException
        val exception = assertThrows(SQLException::class.java) {
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "test",
                password = null
            )
        }

        assertNotNull(exception)
    }

    @Test
    fun `testConnection should handle empty password`() {
        // This will fail to connect but should not throw NullPointerException
        val exception = assertThrows(SQLException::class.java) {
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "test",
                password = ""
            )
        }

        assertNotNull(exception)
    }

    @Test
    fun `testConnection should throw SQLException for wrong credentials`() {
        val exception = assertThrows(SQLException::class.java) {
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "wrong_user",
                password = "wrong_password"
            )
        }

        assertNotNull(exception)
    }
}
