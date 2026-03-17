package com.github.yamert89.postgresql

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.DriverManager

private val logger = KotlinLogging.logger {}

/**
 * Manages PostgreSQL database connections.
 * Handles driver initialization and connection creation.
 */
object PostgresConnectionManager {
    init {
        // Explicitly load PostgreSQL driver (required in IntelliJ plugin context)
        try {
            Class.forName("org.postgresql.Driver")
            logger.debug { "PostgreSQL driver loaded successfully" }
        } catch (e: ClassNotFoundException) {
            logger.error(e) { "PostgreSQL driver not found in classpath" }
            throw IllegalStateException("PostgreSQL driver not found", e)
        }
    }

    /**
     * Creates a connection to a PostgreSQL database.
     *
     * @param host Database host
     * @param port Database port
     * @param database Database name
     * @param username Database username
     * @param password Database password (can be null or empty for passwordless auth)
     * @return JDBC Connection
     * @throws Exception if connection fails
     */
    fun getConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Connection {
        val url = "jdbc:postgresql://$host:$port/$database"
        logger.debug { "Creating connection to $url" }

        return try {
            val conn =
                if (password.isNullOrEmpty()) {
                    DriverManager.getConnection(url, username, "")
                } else {
                    DriverManager.getConnection(url, username, password)
                }
            logger.debug { "Connection created successfully" }
            conn
        } catch (e: Exception) {
            logger.error(e) { "Failed to create connection to $url: ${e.message}" }
            throw e
        }
    }
}
