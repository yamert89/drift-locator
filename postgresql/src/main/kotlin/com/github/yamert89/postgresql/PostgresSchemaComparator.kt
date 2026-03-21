package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.core.SchemaDiff
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Session
import kotliquery.queryOf
import java.sql.Connection
import kotliquery.Connection as KConnection

private val logger = KotlinLogging.logger {}

/**
 * PostgreSQL schema comparator that fetches schema via JDBC.
 */
class PostgresSchemaComparator : SchemaComparator {
    override fun compare(source: DatabaseSchema, target: DatabaseSchema): SchemaDiff {
        val sourceMap = source.objects.associateBy { it.name }
        val targetMap = target.objects.associateBy { it.name }

        val added = target.objects.filter { it.name !in sourceMap }
        val removed = source.objects.filter { it.name !in targetMap }
        val modified = mutableListOf<Pair<DatabaseObject, DatabaseObject>>()

        for ((name, sourceObj) in sourceMap) {
            val targetObj = targetMap[name]
            if (targetObj != null && sourceObj != targetObj) {
                modified.add(sourceObj to targetObj)
            }
        }

        return SchemaDiff(added, removed, modified)
    }

    companion object {
        /**
         * Fetches all schemas from a PostgreSQL database (excluding system schemas).
         */
        fun fetchSchema(connection: Connection): DatabaseSchema {
            return fetchSchema(connection, null)
        }

        /**
         * Fetches a specific schema from a PostgreSQL database.
         * @param connection JDBC connection
         * @param schemaName schema name to fetch, or null to fetch all non-system schemas
         */
        fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema {
            logger.info { "Fetching schema ${schemaName ?: "(all non-system)"}" }
            val session = Session(KConnection(connection))
            val objects = mutableListOf<DatabaseObject>()

            // Fetch tables with columns, indexes, constraints
            logger.debug { "Fetching tables..." }
            fetchTables(session, objects, schemaName)
            logger.debug { "Tables fetched: ${objects.size} objects so far" }

            // Fetch views
            logger.debug { "Fetching views..." }
            fetchViews(session, objects, schemaName)

            // Fetch functions
            logger.debug { "Fetching functions..." }
            fetchFunctions(session, objects, schemaName)

            // Fetch procedures
            logger.debug { "Fetching procedures..." }
            fetchProcedures(session, objects, schemaName)

            // Fetch sequences
            logger.debug { "Fetching sequences..." }
            fetchSequences(session, objects, schemaName)

            logger.info { "Schema fetch complete: ${objects.size} total objects" }
            return DatabaseSchema(objects)
        }

        private fun fetchTables(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val columnsByTable = fetchColumns(session, schemaName)
            val indexesByTable = fetchIndexes(session, schemaName)
            val constraintsByTable = fetchConstraints(session, schemaName)

            // Create table objects
            val allTables = (columnsByTable.keys + indexesByTable.keys + constraintsByTable.keys).toSet()
            for ((schema, table) in allTables) {
                val columns = columnsByTable[schema to table] ?: emptyList()
                val indexes = indexesByTable[schema to table] ?: emptyList()
                val constraints = constraintsByTable[schema to table] ?: emptyList()
                objects.add(
                    PostgresTable(
                        schema = schema,
                        pgObjectName = table,
                        columns = columns,
                        indexes = indexes,
                        constraints = constraints,
                    ),
                )
            }
        }

        private fun fetchColumns(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresColumn>> {
            val columnsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            val schemaFilter = schemaName?.let { "AND table_schema = ?" } ?: ""
            val columnQuery =
                """
                SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                FROM information_schema.columns
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                ORDER BY table_schema, table_name, ordinal_position
                """.trimIndent()
            val query = schemaName?.let { queryOf(columnQuery, it) } ?: queryOf(columnQuery)
            session.forEach(query) { row ->
                val schema = row.string("table_schema")
                val table = row.string("table_name")
                val column = row.toPostgresColumn()
                columnsByTable.getOrPut(schema to table) { mutableListOf() }.add(column)
            }
            return columnsByTable
        }

        private fun fetchIndexes(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresIndex>> {
            val indexesByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresIndex>>()
            val schemaFilter = schemaName?.let { "AND schemaname = ?" } ?: ""
            val indexQuery =
                """
                SELECT schemaname, tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(indexQuery, it) } ?: queryOf(indexQuery)
            session.forEach(query) { row ->
                val schema = row.string("schemaname")
                val table = row.string("tablename")
                val index = row.toPostgresIndex()
                indexesByTable.getOrPut(schema to table) { mutableListOf() }.add(index)
            }
            return indexesByTable
        }

        private fun fetchConstraints(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresConstraint>> {
            val constraintsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresConstraint>>()
            val schemaFilter = schemaName?.let { "AND tc.table_schema = ?" } ?: ""
            val constraintQuery =
                """
                SELECT tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type,
                       string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS columns
                FROM information_schema.table_constraints tc
                LEFT JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                    AND tc.table_name = kcu.table_name
                WHERE tc.table_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                GROUP BY tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type
                """.trimIndent()
            val query = schemaName?.let { queryOf(constraintQuery, it) } ?: queryOf(constraintQuery)
            session.forEach(query) { row ->
                val schema = row.string("table_schema")
                val table = row.string("table_name")
                val constraint = row.toPostgresConstraint()
                constraintsByTable.getOrPut(schema to table) { mutableListOf() }.add(constraint)
            }
            return constraintsByTable
        }

        private fun fetchViews(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            // Fetch view definitions
            val schemaFilter = schemaName?.let { "AND table_schema = ?" } ?: ""
            val viewQuery =
                """
                SELECT table_schema, table_name
                FROM information_schema.views
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                """.trimIndent()
            val viewQueryObj = schemaName?.let { queryOf(viewQuery, it) } ?: queryOf(viewQuery)
            val views = mutableListOf<Pair<String, String>>()
            session.forEach(viewQueryObj) { row ->
                val schema = row.string("table_schema")
                val viewName = row.string("table_name")
                views.add(schema to viewName)
            }

            // Fetch columns for views
            val columnsByView = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            if (views.isNotEmpty()) {
                val placeholders = views.joinToString(",") { "('${it.first}', '${it.second}')" }
                val schemaFilter2 = schemaName?.let { "AND table_schema = ?" } ?: ""
                val columnQuery =
                    """
                    SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                    FROM information_schema.columns
                    WHERE (table_schema, table_name) IN ($placeholders) $schemaFilter2
                    ORDER BY table_schema, table_name, ordinal_position
                    """.trimIndent()
                val columnQueryObj = schemaName?.let { queryOf(columnQuery, it) } ?: queryOf(columnQuery)
                session.forEach(columnQueryObj) { row ->
                    val schema = row.string("table_schema")
                    val viewName = row.string("table_name")
                    val column = row.toPostgresColumn()
                    columnsByView.getOrPut(schema to viewName) { mutableListOf() }.add(column)
                }
            }

            // Create view objects
            for ((schema, viewName) in views) {
                val columns = columnsByView[schema to viewName] ?: emptyList()
                objects.add(
                    PostgresView(
                        schema = schema,
                        pgObjectName = viewName,
                        columns = columns,
                    ),
                )
            }
        }

        private fun fetchFunctions(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            // Fetch functions from pg_proc
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val functionQuery =
                """
                SELECT n.nspname AS schema, p.proname AS function_name,
                       pg_get_function_result(p.oid) AS return_type,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                AND p.prokind = 'f'  -- functions
                """.trimIndent()
            val query = schemaName?.let { queryOf(functionQuery, it) } ?: queryOf(functionQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresFunction())
            }
        }

        private fun fetchProcedures(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            // Fetch procedures from pg_proc where prokind = 'p'
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val procedureQuery =
                """
                SELECT n.nspname AS schema, p.proname AS procedure_name,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                AND p.prokind = 'p'  -- procedures
                """.trimIndent()
            val query = schemaName?.let { queryOf(procedureQuery, it) } ?: queryOf(procedureQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresProcedure())
            }
        }

        private fun fetchSequences(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            // Fetch sequences from information_schema.sequences
            val schemaFilter = schemaName?.let { "AND sequence_schema = ?" } ?: ""
            val sequenceQuery =
                """
                SELECT sequence_schema, sequence_name, data_type, start_value, increment
                FROM information_schema.sequences
                WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(sequenceQuery, it) } ?: queryOf(sequenceQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresSequence())
            }
        }
    }
}
