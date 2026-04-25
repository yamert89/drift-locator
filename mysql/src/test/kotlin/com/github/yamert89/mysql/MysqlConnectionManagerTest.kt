package com.github.yamert89.mysql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.SQLException

class MysqlConnectionManagerTest {
    @Test
    fun `jdbcUrl should contain MySQL protocol and connection flags`() {
        val url = MysqlConnectionManager.jdbcUrl("localhost", 3307, "app")

        assertEquals("jdbc:mysql://localhost:3307/app?useSSL=false&allowPublicKeyRetrieval=true", url)
    }

    @Test
    fun `getConnection should throw SQLException for unreachable port`() {
        val exception =
            assertThrows(SQLException::class.java) {
                MysqlConnectionManager.getConnection(
                    host = "localhost",
                    port = 1,
                    database = "test",
                    username = "test",
                    password = "test",
                )
            }

        assertNotNull(exception.message)
    }

    @Test
    fun `getConnection should handle null password`() {
        val exception =
            assertThrows(SQLException::class.java) {
                MysqlConnectionManager.getConnection(
                    host = "localhost",
                    port = 1,
                    database = "test",
                    username = "test",
                    password = null,
                )
            }

        assertTrue(exception.message?.isNotBlank() ?: true)
    }
}
