package com.github.yamert89.postgresql

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.DriverManager
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

object PostgresConnectionTester {
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
     * Tests connection to a PostgreSQL database.
     * @return true if connection successful
     * @throws Exception if connection fails (exception is logged and re-thrown)
     */
    fun testConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Boolean {
        val url = "jdbc:postgresql://$host:$port/$database"
        val passwordStatus = if (password.isNullOrEmpty()) "not set" else "set"
        logger.info {
            "Testing connection to $url with username='$username', password=$passwordStatus"
        }

        try {
            val conn =
                if (password.isNullOrEmpty()) {
                    logger.debug { "Connecting without password" }
                    DriverManager.getConnection(url, username, "")
                } else {
                    DriverManager.getConnection(url, username, password)
                }
            conn.use { connection ->
                val isValid = connection.isValid(5)
                if (isValid) {
                    logger.info { "Connection to $url successful" }
                } else {
                    logger.warn { "Connection to $url returned invalid status" }
                }
                return isValid
            }
        } catch (e: SQLException) {
            logger.error(e) { "Connection to $url failed: ${e.message}" }
            throw e
        }
    }
}
