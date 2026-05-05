package com.github.yamert89.sqlite

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import java.sql.Connection
import java.sql.ResultSet

object SqliteSchemaFetcher {
    fun fetchSchema(connection: Connection): DatabaseSchema {
        val objects = mutableListOf<DatabaseObject>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT type, name, tbl_name, sql
                FROM sqlite_master
                WHERE type IN ('table', 'view', 'trigger', 'index')
                  AND name NOT LIKE 'sqlite_%'
                ORDER BY type, name
                """.trimIndent(),
            ).use { rs ->
                val tables = mutableListOf<Pair<String, String?>>()
                while (rs.next()) {
                    when (rs.getString("type")) {
                        "table" -> tables += rs.getString("name") to rs.getString("sql")
                        "view" ->
                            objects +=
                                SqliteView(
                                    sqliteObjectName = rs.getString("name"),
                                    definition = normalizeSql(rs.getString("sql")),
                                )

                        "trigger" ->
                            objects +=
                                SqliteTrigger(
                                    sqliteObjectName = rs.getString("name"),
                                    tableName = rs.getString("tbl_name"),
                                    definition = normalizeSql(rs.getString("sql")),
                                )
                    }
                }

                tables.forEach { (tableName, sql) ->
                    objects += fetchTable(connection, tableName, sql)
                }
            }
        }
        return DatabaseSchema(objects)
    }

    private fun fetchTable(
        connection: Connection,
        tableName: String,
        sql: String?,
    ): SqliteTable {
        val normalizedSql = normalizeSql(sql)
        return SqliteTable(
            sqliteObjectName = tableName,
            createSql = normalizedSql,
            withoutRowId = normalizedSql.contains("WITHOUT ROWID", ignoreCase = true),
            strict = normalizedSql.contains("STRICT", ignoreCase = true),
            columns = fetchColumns(connection, tableName),
            indexes = fetchIndexes(connection, tableName),
            foreignKeys = fetchForeignKeys(connection, tableName),
        )
    }

    private fun fetchColumns(connection: Connection, tableName: String): List<SqliteColumn> =
        pragmaQuery(connection, "table_xinfo('$tableName')") { rs ->
            SqliteColumn(
                columnName = rs.getString("name"),
                dataType = rs.getString("type").orEmpty(),
                isNullable = rs.getInt("notnull") == 0,
                defaultValue = rs.getString("dflt_value"),
                primaryKeyPosition = rs.getInt("pk"),
                hidden = rs.getInt("hidden"),
            )
        }

    private fun fetchIndexes(connection: Connection, tableName: String): List<SqliteIndex> =
        pragmaQuery(connection, "index_list('$tableName')") { rs ->
            val indexName = rs.getString("name")
            val definition = findMasterSql(connection, "index", indexName)
            val whereClause = extractWhereClause(definition)
            SqliteIndex(
                sqliteObjectName = indexName,
                tableName = tableName,
                isUnique = rs.getInt("unique") == 1,
                origin = rs.getString("origin"),
                isPartial = rs.getInt("partial") == 1,
                whereClause = whereClause,
                definition = normalizeSql(definition),
                columns = fetchIndexColumns(connection, indexName),
            )
        }

    private fun fetchIndexColumns(connection: Connection, indexName: String): List<SqliteIndexColumn> =
        pragmaQuery(connection, "index_xinfo('$indexName')") { rs ->
            val key = rs.getInt("key")
            if (key == 0) {
                null
            } else {
                val cid = rs.getInt("cid")
                SqliteIndexColumn(
                    columnName = rs.getString("name") ?: "<expr:${rs.getInt("seqno")}>",
                    seqno = rs.getInt("seqno"),
                    cid = cid,
                    isDescending = rs.getInt("desc") == 1,
                )
            }
        }

    private fun fetchForeignKeys(connection: Connection, tableName: String): List<SqliteForeignKey> {
        data class Row(
            val id: Int,
            val referencedTable: String,
            val fromColumn: String,
            val toColumn: String,
            val onUpdate: String,
            val onDelete: String,
            val match: String,
            val seq: Int,
        )

        val rows =
            pragmaQuery(connection, "foreign_key_list('$tableName')") { rs ->
                Row(
                    id = rs.getInt("id"),
                    referencedTable = rs.getString("table"),
                    fromColumn = rs.getString("from"),
                    toColumn = rs.getString("to"),
                    onUpdate = rs.getString("on_update"),
                    onDelete = rs.getString("on_delete"),
                    match = rs.getString("match"),
                    seq = rs.getInt("seq"),
                )
            }

        return rows
            .groupBy(Row::id)
            .values
            .map { group ->
                val ordered = group.sortedBy(Row::seq)
                SqliteForeignKey(
                    foreignKeyId = ordered.first().id,
                    tableName = tableName,
                    referencedTable = ordered.first().referencedTable,
                    fromColumns = ordered.map(Row::fromColumn),
                    toColumns = ordered.map(Row::toColumn),
                    onUpdate = ordered.first().onUpdate,
                    onDelete = ordered.first().onDelete,
                    match = ordered.first().match,
                )
            }
    }

    private fun findMasterSql(
        connection: Connection,
        type: String,
        name: String,
    ): String? =
        connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = ? AND name = ?").use { ps ->
            ps.setString(1, type)
            ps.setString(2, name)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getString("sql") else null
            }
        }

    private fun extractWhereClause(definition: String?): String? {
        val normalized = normalizeSql(definition)
        val marker = " WHERE "
        val index = normalized.indexOf(marker)
        return if (index >= 0) normalized.substring(index + marker.length).trim() else null
    }

    private fun normalizeSql(sql: String?): String = sql?.replace(Regex("\\s+"), " ")?.trim().orEmpty()

    private fun <T> pragmaQuery(
        connection: Connection,
        pragma: String,
        mapper: (ResultSet) -> T?,
    ): List<T> =
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA $pragma").use { rs ->
                buildList {
                    while (rs.next()) {
                        mapper(rs)?.let(::add)
                    }
                }
            }
        }
}
