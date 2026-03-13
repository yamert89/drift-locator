package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.core.SchemaDiff
import kotliquery.Session
import kotliquery.queryOf
import java.sql.Connection
import kotliquery.Connection as KConnection

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
         * Fetches schema from a PostgreSQL database.
         */
        fun fetchSchema(connection: Connection): DatabaseSchema {
            val session = Session(KConnection(connection))
            val objects = mutableListOf<DatabaseObject>()

            // Fetch tables with columns, indexes, constraints
            fetchTables(session, objects)
            // Fetch views
            fetchViews(session, objects)
            // Fetch functions
            fetchFunctions(session, objects)
            // Fetch procedures
            fetchProcedures(session, objects)
            // Fetch sequences
            fetchSequences(session, objects)

            return DatabaseSchema(objects)
        }

        private fun fetchTables(session: Session, objects: MutableList<DatabaseObject>) {
            val columnsByTable = fetchColumns(session)
            val indexesByTable = fetchIndexes(session)
            val constraintsByTable = fetchConstraints(session)

            // Create table objects
            val allTables = (columnsByTable.keys + indexesByTable.keys + constraintsByTable.keys).toSet()
            for ((schema, table) in allTables) {
                val columns = columnsByTable[schema to table] ?: emptyList()
                val indexes = indexesByTable[schema to table] ?: emptyList()
                val constraints = constraintsByTable[schema to table] ?: emptyList()
                objects.add(
                    PostgresTable(
                        schema = schema,
                        objectName = table,
                        columns = columns,
                        indexes = indexes,
                        constraints = constraints,
                    ),
                )
            }
        }

        private fun fetchColumns(session: Session): Map<Pair<String, String>, List<PostgresColumn>> {
            val columnsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            val columnQuery =
                """
                SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                FROM information_schema.columns
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY table_schema, table_name, ordinal_position
                """.trimIndent()
            session.forEach(queryOf(columnQuery)) { row ->
                val schema = row.string("table_schema")
                val table = row.string("table_name")
                val column = row.toPostgresColumn()
                columnsByTable.getOrPut(schema to table) { mutableListOf() }.add(column)
            }
            return columnsByTable
        }

        private fun fetchIndexes(session: Session): Map<Pair<String, String>, List<PostgresIndex>> {
            val indexesByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresIndex>>()
            val indexQuery =
                """
                SELECT schemaname, tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            session.forEach(queryOf(indexQuery)) { row ->
                val schema = row.string("schemaname")
                val table = row.string("tablename")
                val index = row.toPostgresIndex()
                indexesByTable.getOrPut(schema to table) { mutableListOf() }.add(index)
            }
            return indexesByTable
        }

        private fun fetchConstraints(session: Session): Map<Pair<String, String>, List<PostgresConstraint>> {
            val constraintsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresConstraint>>()
            val constraintQuery =
                """
                SELECT tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type,
                       string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS columns
                FROM information_schema.table_constraints tc
                LEFT JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                    AND tc.table_name = kcu.table_name
                WHERE tc.table_schema NOT IN ('pg_catalog', 'information_schema')
                GROUP BY tc.table_schema, tc.table_name, tc.constraint_name, tc.constraint_type
                """.trimIndent()
            session.forEach(queryOf(constraintQuery)) { row ->
                val schema = row.string("table_schema")
                val table = row.string("table_name")
                val constraint = row.toPostgresConstraint()
                constraintsByTable.getOrPut(schema to table) { mutableListOf() }.add(constraint)
            }
            return constraintsByTable
        }

        private fun fetchViews(session: Session, objects: MutableList<DatabaseObject>) {
            // Fetch view definitions
            val viewQuery =
                """
                SELECT table_schema, table_name
                FROM information_schema.views
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            val views = mutableListOf<Pair<String, String>>()
            session.forEach(queryOf(viewQuery)) { row ->
                val schema = row.string("table_schema")
                val viewName = row.string("table_name")
                views.add(schema to viewName)
            }

            // Fetch columns for views
            val columnsByView = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            if (views.isNotEmpty()) {
                val placeholders = views.joinToString(",") { "('${it.first}', '${it.second}')" }
                val columnQuery =
                    """
                    SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                    FROM information_schema.columns
                    WHERE (table_schema, table_name) IN ($placeholders)
                    ORDER BY table_schema, table_name, ordinal_position
                    """.trimIndent()
                session.forEach(queryOf(columnQuery)) { row ->
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
                        objectName = viewName,
                        columns = columns,
                    ),
                )
            }
        }

        private fun fetchFunctions(session: Session, objects: MutableList<DatabaseObject>) {
            // Fetch functions from pg_proc
            val functionQuery =
                """
                SELECT n.nspname AS schema, p.proname AS function_name,
                       pg_get_function_result(p.oid) AS return_type,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                AND p.prokind = 'f'  -- functions
                """.trimIndent()
            session.forEach(queryOf(functionQuery)) { row ->
                objects.add(row.toPostgresFunction())
            }
        }

        private fun fetchProcedures(session: Session, objects: MutableList<DatabaseObject>) {
            // Fetch procedures from pg_proc where prokind = 'p'
            val procedureQuery =
                """
                SELECT n.nspname AS schema, p.proname AS procedure_name,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                AND p.prokind = 'p'  -- procedures
                """.trimIndent()
            session.forEach(queryOf(procedureQuery)) { row ->
                objects.add(row.toPostgresProcedure())
            }
        }

        private fun fetchSequences(session: Session, objects: MutableList<DatabaseObject>) {
            // Fetch sequences from information_schema.sequences
            val sequenceQuery =
                """
                SELECT sequence_schema, sequence_name, data_type, start_value, increment
                FROM information_schema.sequences
                WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            session.forEach(queryOf(sequenceQuery)) { row ->
                objects.add(row.toPostgresSequence())
            }
        }
    }
}
