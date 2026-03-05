package com.github.yamert89.postgresql

import com.github.yamert89.core.*
import java.sql.Connection
import java.sql.DriverManager

/**
 * PostgreSQL-specific database object.
 */
data class PostgresTable(
    override val name: String,
    val schema: String,
    val columns: List<PostgresColumn>
) : DatabaseObject {
    override val type: String = "TABLE"
}

data class PostgresColumn(
    val name: String,
    val dataType: String,
    val isNullable: Boolean
)

/**
 * PostgreSQL schema comparator that fetches schema via JDBC.
 */
class PostgresSchemaComparator(private val connection: Connection) : SchemaComparator {
    override fun compare(source: DatabaseSchema, target: DatabaseSchema): SchemaDiff {
        // Simplified comparison: treat all objects as generic
        val sourceMap = source.objects.associateBy { it.name }
        val targetMap = target.objects.associateBy { it.name }

        val added = target.objects.filter { it.name !in sourceMap }
        val removed = source.objects.filter { it.name !in targetMap }
        val modified = emptyList<Pair<DatabaseObject, DatabaseObject>>() // placeholder

        return SchemaDiff(added, removed, modified)
    }

    companion object {
        /**
         * Fetches schema from a PostgreSQL database.
         */
        fun fetchSchema(connection: Connection): DatabaseSchema {
            val tables = mutableListOf<DatabaseObject>()
            val rs = connection.createStatement().executeQuery(
                """
                SELECT table_schema, table_name
                FROM information_schema.tables
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                """
            )
            while (rs.next()) {
                val schema = rs.getString("table_schema")
                val tableName = rs.getString("table_name")
                tables.add(PostgresTable("$schema.$tableName", schema, emptyList()))
            }
            rs.close()
            return DatabaseSchema(tables)
        }
    }
}