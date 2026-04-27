package com.github.yamert89.mysql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Session
import kotliquery.queryOf
import java.sql.Connection
import kotliquery.Connection as KConnection

private val logger = KotlinLogging.logger {}

/**
 * Fetches MySQL schema snapshots via JDBC.
 */
object MysqlSchemaFetcher {
    private val systemSchemas = setOf("mysql", "information_schema", "performance_schema", "sys")

    fun fetchSchema(connection: Connection): DatabaseSchema = fetchSchema(connection, null)

    fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema {
    logger.info { "Fetching MySQL schema ${schemaName ?: "(all non-system)"}" }
    val session = Session(KConnection(connection))
    val objects = mutableListOf<DatabaseObject>()
    fetchSafely("tables") { fetchTables(session, objects, schemaName) }
    fetchSafely("views") { fetchViews(session, objects, schemaName) }
    fetchSafely("routines") { fetchRoutines(session, objects, schemaName) }
    fetchSafely("triggers") { fetchTriggers(session, objects, schemaName) }
    fetchSafely("events") { fetchEvents(session, objects, schemaName) }
    fetchSafely("partitions") { fetchPartitions(session, objects, schemaName) }
    if (schemaName == null) {
        fetchSafely("schemas") { fetchSchemas(session, objects) }
        fetchSafely("grants") { fetchGrants(session, objects) }
        fetchSafely("users") { fetchUsers(session, objects) }
        fetchSafely("tablespaces") { fetchTablespaces(session, objects) }
    }
    logger.info { "MySQL schema fetch complete: ${objects.size} total objects" }
    return DatabaseSchema(objects)
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun fetchSafely(objectType: String, fetcher: () -> Unit) {
    try {
        logger.debug { "Fetching MySQL $objectType..." }
        fetcher()
    } catch (e: Throwable) {
        logger.warn(e) { "Failed to fetch MySQL $objectType, skipping: ${e.message}" }
    }
    }

    private fun schemaPredicate(columnName: String, schemaName: String?): String =
    schemaName?.let { "AND $columnName = ?" }
        ?: "AND $columnName NOT IN (${systemSchemas.joinToString(",") { "'$it'" }})"

    private fun query(sql: String, schemaName: String?) = schemaName?.let { queryOf(sql, it) } ?: queryOf(sql)

    private fun fetchTables(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String?,
    ) {
        val columnsByTable = fetchColumns(session, schemaName)
        val indexesByTable = fetchIndexes(session, schemaName)
        val constraintsByTable = fetchConstraints(session, schemaName)
        val tableSql =
            """
            SELECT table_schema, table_name, engine, table_collation
            FROM information_schema.tables
            WHERE table_type = 'BASE TABLE' ${schemaPredicate("table_schema", schemaName)}
            ORDER BY table_schema, table_name
            """.trimIndent()
        session.forEach(query(tableSql, schemaName)) { row ->
            val schema = row.string("table_schema")
            val table = row.string("table_name")
            objects.add(
                MysqlTable(
                    schema = schema,
                    mysqlObjectName = table,
                    engine = row.stringOrNull("engine"),
                    collation = row.stringOrNull("table_collation"),
                    columns = columnsByTable[schema to table] ?: emptyList(),
                    indexes = indexesByTable[schema to table] ?: emptyList(),
                    constraints = constraintsByTable[schema to table] ?: emptyList(),
                ),
            )
        }
    }

    private fun fetchColumns(session: Session, schemaName: String?): Map<Pair<String, String>, List<MysqlColumn>> {
        val columnsByTable = mutableMapOf<Pair<String, String>, MutableList<MysqlColumn>>()
        val sql =
            """
            SELECT table_schema, table_name, column_name, data_type, column_type, is_nullable,
                   column_default, ordinal_position, extra, generation_expression
            FROM information_schema.columns
            WHERE 1 = 1 ${schemaPredicate("table_schema", schemaName)}
            ORDER BY table_schema, table_name, ordinal_position
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val key = row.string("table_schema") to row.string("table_name")
            columnsByTable.getOrPut(key) { mutableListOf() }.add(row.toMysqlColumn())
        }
        return columnsByTable
    }

    private fun fetchIndexes(session: Session, schemaName: String?): Map<Pair<String, String>, List<MysqlIndex>> {
        val indexesByTable = mutableMapOf<Pair<String, String>, MutableList<MysqlIndex>>()
        val sql =
            """
            SELECT table_schema, table_name, index_name, non_unique, index_type,
                   GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS index_columns
            FROM information_schema.statistics
            WHERE 1 = 1 ${schemaPredicate("table_schema", schemaName)}
            GROUP BY table_schema, table_name, index_name, non_unique, index_type
            ORDER BY table_schema, table_name, index_name
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val key = row.string("table_schema") to row.string("table_name")
            val columns = row.stringOrNull("index_columns")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            indexesByTable.getOrPut(key) { mutableListOf() }.add(
                MysqlIndex(
                    indexName = row.string("index_name"),
                    columns = columns,
                    isUnique = row.int("non_unique") == 0,
                    indexType = row.string("index_type"),
                ),
            )
        }
        return indexesByTable
    }

    private fun fetchConstraints(session: Session, schemaName: String?): Map<Pair<String, String>, List<MysqlConstraint>> {
        val constraintsByTable = mutableMapOf<Pair<String, String>, MutableList<MysqlConstraint>>()
        val sql =
            """
            SELECT tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type,
                   GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position SEPARATOR ',') AS columns,
                   MAX(kcu.referenced_table_name) AS referenced_table,
                   GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.position_in_unique_constraint SEPARATOR ',') AS referenced_columns,
                   MAX(cc.check_clause) AS check_clause
            FROM information_schema.table_constraints tc
            LEFT JOIN information_schema.key_column_usage kcu
                ON tc.constraint_schema = kcu.constraint_schema
                AND tc.table_schema = kcu.table_schema
                AND tc.table_name = kcu.table_name
                AND tc.constraint_name = kcu.constraint_name
            LEFT JOIN information_schema.check_constraints cc
                ON tc.constraint_schema = cc.constraint_schema
                AND tc.constraint_name = cc.constraint_name
            WHERE 1 = 1 ${schemaPredicate("tc.table_schema", schemaName)}
            GROUP BY tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val key = row.string("table_schema") to row.string("table_name")
            val columns = row.stringOrNull("columns")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            val referencedColumns =
                row.stringOrNull("referenced_columns")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            constraintsByTable.getOrPut(key) { mutableListOf() }.add(
                MysqlConstraint(
                    constraintName = row.string("constraint_name"),
                    constraintType = row.string("constraint_type"),
                    tableName = row.string("table_name"),
                    columns = columns,
                    referencedTable = row.stringOrNull("referenced_table"),
                    referencedColumns = referencedColumns,
                    checkClause = row.stringOrNull("check_clause"),
                ),
            )
        }
        return constraintsByTable
    }

    private fun fetchViews(
        session: Session,
        objects: MutableList<DatabaseObject>,
        schemaName: String?,
    ) {
        val columnsByTable = fetchColumns(session, schemaName)
        val sql =
            """
            SELECT table_schema, table_name, view_definition, check_option
            FROM information_schema.views
            WHERE 1 = 1 ${schemaPredicate("table_schema", schemaName)}
            ORDER BY table_schema, table_name
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val key = row.string("table_schema") to row.string("table_name")
            objects.add(row.toMysqlView(columnsByTable[key] ?: emptyList()))
        }
    }

    private fun fetchRoutines(
        session: Session,
        objects: MutableList<DatabaseObject>,
        schemaName: String?,
    ) {
        val parametersByRoutine = fetchRoutineParameters(session, schemaName)
        val sql =
            """
            SELECT routine_schema, routine_name, routine_type, data_type, routine_definition
            FROM information_schema.routines
            WHERE 1 = 1 ${schemaPredicate("routine_schema", schemaName)}
            ORDER BY routine_schema, routine_name
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val schema = row.string("routine_schema")
            val name = row.string("routine_name")
            val type = row.string("routine_type")
            objects.add(
                MysqlRoutine(
                    schema = schema,
                    mysqlObjectName = name,
                    routineType = type,
                    returnType = row.stringOrNull("data_type"),
                    parameters = parametersByRoutine[Triple(schema, name, type)] ?: emptyList(),
                    body = row.stringOrNull("routine_definition"),
                ),
            )
        }
    }

    private fun fetchRoutineParameters(
        session: Session,
        schemaName: String?,
    ): Map<Triple<String, String, String>, List<MysqlRoutineParameter>> {
        val parameters = mutableMapOf<Triple<String, String, String>, MutableList<MysqlRoutineParameter>>()
        val sql =
            """
            SELECT specific_schema, specific_name, routine_type, parameter_name,
                   parameter_mode, data_type, ordinal_position
            FROM information_schema.parameters
            WHERE parameter_name IS NOT NULL ${schemaPredicate("specific_schema", schemaName)}
            ORDER BY specific_schema, specific_name, ordinal_position
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row ->
            val key = Triple(row.string("specific_schema"), row.string("specific_name"), row.string("routine_type"))
            parameters.getOrPut(key) { mutableListOf() }.add(
                MysqlRoutineParameter(
                    parameterName = row.string("parameter_name"),
                    mode = row.stringOrNull("parameter_mode"),
                    dataType = row.string("data_type"),
                    ordinalPosition = row.int("ordinal_position"),
                ),
            )
        }
        return parameters
    }

    private fun fetchTriggers(
        session: Session,
        objects: MutableList<DatabaseObject>,
        schemaName: String?,
    ) {
        val sql =
            """
            SELECT trigger_schema, trigger_name, event_object_table, event_manipulation,
                   action_timing, action_statement
            FROM information_schema.triggers
            WHERE 1 = 1 ${schemaPredicate("trigger_schema", schemaName)}
            ORDER BY trigger_schema, trigger_name
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row -> objects.add(row.toMysqlTrigger()) }
    }

    private fun fetchEvents(
        session: Session,
        objects: MutableList<DatabaseObject>,
        schemaName: String?,
    ) {
        val sql =
            """
            SELECT event_schema, event_name, event_definition, interval_value, interval_field, status
            FROM information_schema.events
            WHERE 1 = 1 ${schemaPredicate("event_schema", schemaName)}
            ORDER BY event_schema, event_name
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row -> objects.add(row.toMysqlEvent()) }
    }

    private fun fetchPartitions(
        session: Session,
        objects: MutableList<DatabaseObject>,
        schemaName: String?,
    ) {
        val sql =
            """
            SELECT table_schema, table_name, partition_name, partition_method,
                   partition_expression, partition_description
            FROM information_schema.partitions
            WHERE partition_name IS NOT NULL ${schemaPredicate("table_schema", schemaName)}
            ORDER BY table_schema, table_name, partition_ordinal_position
            """.trimIndent()
        session.forEach(query(sql, schemaName)) { row -> objects.add(row.toMysqlPartition()) }
    }

    private fun fetchSchemas(session: Session, objects: MutableList<DatabaseObject>) {
        val sql =
            """
            SELECT schema_name, default_character_set_name, default_collation_name
            FROM information_schema.schemata
            WHERE schema_name NOT IN (${systemSchemas.joinToString(",") { "'$it'" }})
            ORDER BY schema_name
            """.trimIndent()
        session.forEach(queryOf(sql)) { row -> objects.add(row.toMysqlSchemaObject()) }
    }

    private fun fetchGrants(session: Session, objects: MutableList<DatabaseObject>) {
        val sql =
            """
            SELECT grantee, 'GLOBAL' AS object_type, '*' AS object_name, privilege_type, is_grantable
            FROM information_schema.user_privileges
            UNION ALL
            SELECT grantee, 'SCHEMA' AS object_type, table_schema AS object_name, privilege_type, is_grantable
            FROM information_schema.schema_privileges
            UNION ALL
            SELECT grantee, 'TABLE' AS object_type, CONCAT(table_schema, '.', table_name) AS object_name,
                   privilege_type, is_grantable
            FROM information_schema.table_privileges
            UNION ALL
            SELECT grantee, 'COLUMN' AS object_type, CONCAT(table_schema, '.', table_name, '.', column_name) AS object_name,
                   privilege_type, is_grantable
            FROM information_schema.column_privileges
            """.trimIndent()
        session.forEach(queryOf(sql)) { row ->
            objects.add(
                MysqlGrant(
                    grantee = row.string("grantee"),
                    objectType = row.string("object_type"),
                    targetObjectName = row.string("object_name"),
                    privilege = row.string("privilege_type"),
                    isGrantable = row.string("is_grantable") == "YES",
                ),
            )
        }
    }

    private fun fetchUsers(session: Session, objects: MutableList<DatabaseObject>) {
        val sql =
            """
            SELECT user, host, account_locked
            FROM mysql.user
            ORDER BY user, host
            """.trimIndent()
        session.forEach(queryOf(sql)) { row -> objects.add(row.toMysqlUser()) }
    }

    private fun fetchTablespaces(session: Session, objects: MutableList<DatabaseObject>) {
        val sql =
            """
            SELECT DISTINCT tablespace_name, engine
            FROM information_schema.files
            WHERE tablespace_name IS NOT NULL
            ORDER BY tablespace_name
            """.trimIndent()
        session.forEach(queryOf(sql)) { row -> objects.add(row.toMysqlTablespace()) }
    }
}
