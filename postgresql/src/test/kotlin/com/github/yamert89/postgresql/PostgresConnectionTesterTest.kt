package com.github.yamert89.postgresql

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for PostgresConnectionTester.
 * Note: Full connection tests with actual PostgreSQL are in PostgresSchemaComparatorIntegrationTest with Testcontainers.
 */
class PostgresConnectionTesterTest {
    @Test
    fun `testConnection should return failure for invalid host`() {
        val result =
            PostgresConnectionTester.testConnection(
                host = "invalid_host_12345",
                port = 5432,
                database = "test",
                username = "test",
                password = "test",
            )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection should return failure for unreachable port`() {
        val result =
            PostgresConnectionTester.testConnection(
                host = "localhost",
                // Invalid port
                port = 1,
                database = "test",
                username = "test",
                password = "test",
            )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `testConnection should handle null password`() {
        // This will fail to connect but should not throw NullPointerException
        val result =
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "test",
                password = null,
            )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `testConnection should handle empty password`() {
        // This will fail to connect but should not throw NullPointerException
        val result =
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "test",
                password = "",
            )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `testConnection should return failure for wrong credentials`() {
        val result =
            PostgresConnectionTester.testConnection(
                host = "localhost",
                port = 1,
                database = "test",
                username = "wrong_user",
                password = "wrong_password",
            )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
