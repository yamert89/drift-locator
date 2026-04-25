package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.Defaults
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.mysql.MysqlConnectionManager
import com.github.yamert89.mysql.MysqlConnectionTester
import com.github.yamert89.mysql.MysqlMeta
import com.github.yamert89.mysql.MysqlSchemaComparator
import com.github.yamert89.mysql.mysqlJdbcUrl
import com.github.yamert89.postgresql.PgMeta
import com.github.yamert89.postgresql.PostgresConnectionManager
import com.github.yamert89.postgresql.PostgresConnectionTester
import com.github.yamert89.postgresql.PostgresSchemaComparator
import java.sql.Connection

interface DatabaseAdapter {
    val type: DatabaseType
    val meta: DatabaseMeta
    val comparator: SchemaComparator

    fun jdbcUrl(
        host: String,
        port: Int,
        database: String,
    ): String

    fun testConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Result<Boolean>

    fun getConnection(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: String?,
    ): Connection

    fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema
}

object DatabaseAdapters {
    private val postgres =
        object : DatabaseAdapter {
            override val type = DatabaseType.POSTGRESQL
            override val meta = PgMeta()
            override val comparator = PostgresSchemaComparator()

            override fun jdbcUrl(
                host: String,
                port: Int,
                database: String,
            ): String = "jdbc:postgresql://$host:$port/$database"

            override fun testConnection(
                host: String,
                port: Int,
                database: String,
                username: String,
                password: String?,
            ): Result<Boolean> = PostgresConnectionTester.testConnection(host, port, database, username, password)

            override fun getConnection(
                host: String,
                port: Int,
                database: String,
                username: String,
                password: String?,
            ): Connection = PostgresConnectionManager.getConnection(host, port, database, username, password)

            override fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema =
                PostgresSchemaComparator.fetchSchema(connection, schemaName)
        }

    private val mysql =
        object : DatabaseAdapter {
            override val type = DatabaseType.MYSQL
            override val meta = MysqlMeta()
            override val comparator = MysqlSchemaComparator()

            override fun jdbcUrl(
                host: String,
                port: Int,
                database: String,
            ): String = mysqlJdbcUrl(host, port, database)

            override fun testConnection(
                host: String,
                port: Int,
                database: String,
                username: String,
                password: String?,
            ): Result<Boolean> = MysqlConnectionTester.testConnection(host, port, database, username, password)

            override fun getConnection(
                host: String,
                port: Int,
                database: String,
                username: String,
                password: String?,
            ): Connection = MysqlConnectionManager.getConnection(host, port, database, username, password)

            override fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema =
                MysqlSchemaComparator.fetchSchema(connection, schemaName)
        }

    private val adapters = listOf(postgres, mysql).associateBy { it.type }

    fun forType(type: DatabaseType): DatabaseAdapter = adapters.getValue(type)

    fun defaults(type: DatabaseType): Defaults = forType(type).meta.getDefaults()
}
