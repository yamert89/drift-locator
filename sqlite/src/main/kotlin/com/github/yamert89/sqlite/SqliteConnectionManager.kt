package com.github.yamert89.sqlite

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

private val sqliteConnectionLogger = KotlinLogging.logger {}

object SqliteConnectionManager {
    init {
        try {
            Class.forName("org.sqlite.JDBC")
            sqliteConnectionLogger.debug { "SQLite driver loaded successfully" }
        } catch (e: ClassNotFoundException) {
            sqliteConnectionLogger.error(e) { "SQLite driver not found in classpath" }
            throw IllegalStateException("SQLite driver not found", e)
        }
    }

    fun jdbcUrl(filePath: String): String {
        val uri = Path.of(filePath).toAbsolutePath().normalize().toUri().toString()
        return "jdbc:sqlite:$uri?mode=ro"
    }

    fun getConnection(filePath: String): Connection {
        val normalizedPath = validateFilePath(filePath)
        val url = jdbcUrl(normalizedPath.toString())
        val properties =
            Properties().apply {
                setProperty("open_mode", "1")
                setProperty("journal_mode", "OFF")
            }
        return try {
            DriverManager.getConnection(url, properties).also {
                sqliteConnectionLogger.debug { "SQLite connection created successfully for $normalizedPath" }
            }
        } catch (e: SQLException) {
            sqliteConnectionLogger.warn(e) { "Failed to create SQLite connection to $normalizedPath: ${e.message}" }
            throw e
        }
    }

    fun validateFilePath(filePath: String): Path {
        require(filePath.isNotBlank()) { "SQLite database file path must not be empty" }
        val path = Path.of(filePath).toAbsolutePath().normalize()
        require(Files.exists(path)) { "SQLite database file does not exist: $path" }
        require(Files.isRegularFile(path)) { "SQLite database path must point to a file: $path" }
        require(Files.isReadable(path)) { "SQLite database file is not readable: $path" }
        return path
    }
}
