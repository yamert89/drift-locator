package com.github.yamert89.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseConnectionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `old serialized connection without databaseType should decode as PostgreSQL`() {
        val connection =
            json.decodeFromString(
                serializer<DatabaseConnection>(),
                """
                {
                  "id": "prod",
                  "name": "prod",
                  "host": "localhost",
                  "port": 5432,
                  "database": "postgres",
                  "username": "postgres",
                  "schema": "public",
                  "savePassword": true,
                  "isValid": true
                }
                """.trimIndent(),
            )

        assertEquals(DatabaseType.POSTGRESQL, connection.databaseType)
        assertEquals("jdbc:postgresql://localhost:5432/postgres", connection.url)
    }

    @Test
    fun `MySQL databaseType should serialize and restore`() {
        val connection =
            DatabaseConnection(
                id = "local-mysql",
                name = "local-mysql",
                host = "127.0.0.1",
                port = 3306,
                database = "app",
                databaseType = DatabaseType.MYSQL,
                username = "root",
                schema = "app",
            )

        val encoded = json.encodeToString(serializer<DatabaseConnection>(), connection)
        val decoded = json.decodeFromString(serializer<DatabaseConnection>(), encoded)

        assertTrue(encoded.contains("\"databaseType\""))
        assertEquals(DatabaseType.MYSQL, decoded.databaseType)
        assertEquals("jdbc:mysql://127.0.0.1:3306/app?useSSL=false&allowPublicKeyRetrieval=true", decoded.url)
    }

    @Test
    fun `SQLite databaseType should serialize and restore`() {
        val connection =
            DatabaseConnection(
                id = "local-sqlite",
                name = "local-sqlite",
                databaseType = DatabaseType.SQLITE,
                filePath = "/tmp/app.db",
                savePassword = false,
            )

        val encoded = json.encodeToString(serializer<DatabaseConnection>(), connection)
        val decoded = json.decodeFromString(serializer<DatabaseConnection>(), encoded)

        assertTrue(encoded.contains("\"databaseType\""))
        assertTrue(encoded.contains("\"filePath\""))
        assertEquals(DatabaseType.SQLITE, decoded.databaseType)
        assertTrue(decoded.url.contains("jdbc:sqlite:"))
        assertEquals("/tmp/app.db", decoded.filePath)
    }

    @Test
    fun `password should remain transient`() {
        val connection =
            DatabaseConnection(
                id = "mysql",
                name = "mysql",
                host = "localhost",
                port = 3306,
                database = "app",
                databaseType = DatabaseType.MYSQL,
                username = "root",
                schema = "app",
            ).withPassword("secret")

        val encoded = json.encodeToString(serializer<DatabaseConnection>(), connection)

        assertFalseContains(encoded, "secret")
    }

    @Test
    fun `PostgreSQL report metadata includes connection endpoint and schema`() {
        val connection =
            DatabaseConnection(
                id = "prod",
                name = "Production",
                host = "db.example.com",
                port = 5432,
                database = "app",
                databaseType = DatabaseType.POSTGRESQL,
                schema = "public",
            )

        assertEquals(
            listOf(
                "Connection" to "Production",
                "Endpoint" to "db.example.com:5432/app",
            ),
            connection.reportHeaderDetails(),
        )
        assertEquals("Schema", connection.reportScopeLabel())
    }

    @Test
    fun `MySQL report metadata uses database scope label`() {
        val connection =
            DatabaseConnection(
                id = "mysql",
                name = "Analytics",
                host = "mysql.internal",
                port = 3306,
                database = "warehouse",
                databaseType = DatabaseType.MYSQL,
            )

        assertEquals("Database", connection.reportScopeLabel())
        assertEquals("warehouse", connection.displayScope())
        assertEquals(
            listOf(
                "Connection" to "Analytics",
                "Endpoint" to "mysql.internal:3306/warehouse",
            ),
            connection.reportHeaderDetails(),
        )
    }

    @Test
    fun `SQLite report metadata uses file path and no scope label`() {
        val connection =
            DatabaseConnection(
                id = "sqlite",
                name = "Local cache",
                databaseType = DatabaseType.SQLITE,
                filePath = "/tmp/app.db",
            )

        assertEquals(null, connection.reportScopeLabel())
        assertEquals(
            listOf(
                "Connection" to "Local cache",
                "Path" to "/tmp/app.db",
            ),
            connection.reportHeaderDetails(),
        )
    }

    private fun assertFalseContains(text: String, fragment: String) {
        assertTrue(!text.contains(fragment), "Did not expect '$fragment' in '$text'")
    }
}
