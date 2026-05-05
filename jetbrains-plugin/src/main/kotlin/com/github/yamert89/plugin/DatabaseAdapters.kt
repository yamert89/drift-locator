package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.Defaults
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.mysql.MysqlConnectionManager
import com.github.yamert89.mysql.MysqlConnectionTester
import com.github.yamert89.mysql.MysqlMeta
import com.github.yamert89.mysql.MysqlSchemaComparator
import com.github.yamert89.mysql.MysqlSchemaFetcher
import com.github.yamert89.mysql.mysqlJdbcUrl
import com.github.yamert89.postgresql.PgMeta
import com.github.yamert89.postgresql.PostgresConnectionManager
import com.github.yamert89.postgresql.PostgresConnectionTester
import com.github.yamert89.postgresql.PostgresSchemaComparator
import com.github.yamert89.postgresql.PostgresSchemaFetcher
import com.github.yamert89.sqlite.SqliteConnectionManager
import com.github.yamert89.sqlite.SqliteConnectionTester
import com.github.yamert89.sqlite.SqliteMeta
import com.github.yamert89.sqlite.SqliteSchemaComparator
import com.github.yamert89.sqlite.SqliteSchemaFetcher
import java.sql.Connection

interface DatabaseAdapter {
    val type: DatabaseType
    val meta: DatabaseMeta
    val comparator: SchemaComparator

    fun jdbcUrl(connection: DatabaseConnection): String

    fun testConnection(connection: DatabaseConnection): Result<Boolean>

    fun getConnection(connection: DatabaseConnection): Connection

    fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema
}

object DatabaseAdapters {
    private val postgres =
        object : DatabaseAdapter {
            override val type = DatabaseType.POSTGRESQL
            override val meta = PgMeta()
            override val comparator = PostgresSchemaComparator()

            override fun jdbcUrl(connection: DatabaseConnection): String =
                "jdbc:postgresql://${connection.host}:${connection.port}/${connection.database}"

            override fun testConnection(connection: DatabaseConnection): Result<Boolean> =
                PostgresConnectionTester.testConnection(
                    connection.host,
                    connection.port,
                    connection.database,
                    connection.username,
                    connection.password,
                )

            override fun getConnection(connection: DatabaseConnection): Connection =
                PostgresConnectionManager.getConnection(
                    connection.host,
                    connection.port,
                    connection.database,
                    connection.username,
                    connection.password,
                )

            override fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema =
                PostgresSchemaFetcher.fetchSchema(connection, schemaName)
        }

    private val mysql =
        object : DatabaseAdapter {
            override val type = DatabaseType.MYSQL
            override val meta = MysqlMeta()
            override val comparator = MysqlSchemaComparator()

            override fun jdbcUrl(connection: DatabaseConnection): String =
                mysqlJdbcUrl(connection.host, connection.port, connection.database)

            override fun testConnection(connection: DatabaseConnection): Result<Boolean> =
                MysqlConnectionTester.testConnection(
                    connection.host,
                    connection.port,
                    connection.database,
                    connection.username,
                    connection.password,
                )

            override fun getConnection(connection: DatabaseConnection): Connection =
                MysqlConnectionManager.getConnection(
                    connection.host,
                    connection.port,
                    connection.database,
                    connection.username,
                    connection.password,
                )

            override fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema =
                MysqlSchemaFetcher.fetchSchema(connection, schemaName)
        }

    private val sqlite =
        object : DatabaseAdapter {
            override val type = DatabaseType.SQLITE
            override val meta = SqliteMeta()
            override val comparator = SqliteSchemaComparator()

            override fun jdbcUrl(connection: DatabaseConnection): String = SqliteConnectionManager.jdbcUrl(connection.filePath)

            override fun testConnection(connection: DatabaseConnection): Result<Boolean> =
                SqliteConnectionTester.testConnection(connection.filePath)

            override fun getConnection(connection: DatabaseConnection): Connection =
                SqliteConnectionManager.getConnection(connection.filePath)

            override fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema =
                SqliteSchemaFetcher.fetchSchema(connection)
        }

    private val adapters = listOf(postgres, mysql, sqlite).associateBy { it.type }

    fun forType(type: DatabaseType): DatabaseAdapter = adapters.getValue(type)

    fun defaults(type: DatabaseType): Defaults = forType(type).meta.getDefaults()
}
