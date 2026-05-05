package com.github.yamert89.sqlite

import io.github.oshai.kotlinlogging.KotlinLogging

private val sqliteTesterLogger = KotlinLogging.logger {}

object SqliteConnectionTester {
    fun testConnection(filePath: String): Result<Boolean> =
        runCatching {
            val path = SqliteConnectionManager.validateFilePath(filePath)
            sqliteTesterLogger.info { "Testing SQLite connection to $path" }
            SqliteConnectionManager.getConnection(path.toString()).use { connection ->
                connection.isValid(5)
            }
        }
}
