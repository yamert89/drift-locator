package com.github.yamert89.mysql

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

private val connectionLogger = KotlinLogging.logger {}

fun mysqlJdbcUrl(
    host: String,
    port: Int,
    database: String,
): String {
    return "jdbc:mysql://$host:$port/$database?useSSL=false&allowPublicKeyRetrieval=true"
}

object MysqlConnectionManager {
    init {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
            connectionLogger.debug { "MySQL driver loaded successfully" }
        } catch (e: ClassNotFoundException) {
            connectionLogger.error(e) { "MySQL driver not found in classpath" }
            throw IllegalStateException("MySQL driver not found", e)
        }
    }

    fun getConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Connection {
        val url = jdbcUrl(host, port, database)
        connectionLogger.debug { "Creating connection to $url" }
        try {
            val connection =
                if (password.isNullOrEmpty()) {
                    DriverManager.getConnection(url, username, "")
                } else {
                    DriverManager.getConnection(url, username, password)
                }
            connectionLogger.debug { "MySQL connection created successfully" }
            return connection
        } catch (e: SQLException) {
            connectionLogger.warn(e) { "Failed to create MySQL connection to $url: ${e.message}" }
            throw e
        }
    }

    fun jdbcUrl(
        host: String,
        port: Int,
        database: String,
    ): String {
        return mysqlJdbcUrl(host, port, database)
    }
}
