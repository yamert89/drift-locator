package com.github.yamert89.postgresql

import java.sql.DriverManager

object PostgresConnectionTester {
    /**
     * Tests connection to a PostgreSQL database.
     * @return true if connection successful, false otherwise.
     */
    fun testConnection(host: String, port: Int, database: String, username: String, password: String): Boolean {
        val url = "jdbc:postgresql://$host:$port/$database"
        return try {
            DriverManager.getConnection(url, username, password).use { connection ->
                connection.isValid(5)
            }
        } catch (e: Exception) {
            false
        }
    }
}