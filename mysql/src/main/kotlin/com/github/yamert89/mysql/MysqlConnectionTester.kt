package com.github.yamert89.mysql

import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.SQLException

private val testerLogger = KotlinLogging.logger {}

object MysqlConnectionTester {
    fun testConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Result<Boolean> {
        val url = mysqlJdbcUrl(host, port, database)
        val passwordStatus = if (password.isNullOrEmpty()) "not set" else "set"
        testerLogger.info {
            "Testing MySQL connection to $url with username='$username', password=$passwordStatus"
        }

        return try {
            MysqlConnectionManager.getConnection(host, port, database, username, password).use { connection ->
                Result.success(connection.isValid(5))
            }
        } catch (e: SQLException) {
            testerLogger.warn(e) { "MySQL connection to $url failed: ${e.message}" }
            Result.failure(e)
        }
    }
}
