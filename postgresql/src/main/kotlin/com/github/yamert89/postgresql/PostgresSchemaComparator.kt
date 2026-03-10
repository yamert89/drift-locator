package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.core.SchemaDiff
import java.sql.Connection

/**
 * PostgreSQL-specific database object.
 */
sealed class PostgresObject : DatabaseObject {
    abstract val schema: String
    abstract val objectName: String
    override val name: String get() = "$schema.$objectName"
}

/**
 * Represents a PostgreSQL table.
 */
data class PostgresTable(
    override val schema: String,
    override val objectName: String,
    val columns: List<PostgresColumn>,
    val indexes: List<PostgresIndex>,
    val constraints: List<PostgresConstraint>
) : PostgresObject() {
    override val type: String = "TABLE"
    override val children: List<DatabaseObject> = columns + indexes + constraints
}

/**
 * Represents a PostgreSQL column.
 */
data class PostgresColumn(
    val columnName: String,
    val dataType: String,
    val isNullable: Boolean,
    val defaultValue: String?,
    val ordinalPosition: Int
) : DatabaseObject {
    override val name: String = columnName
    override val type: String = "COLUMN"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL index.
 */
data class PostgresIndex(
    val indexName: String,
    val indexDefinition: String,
    val isUnique: Boolean
) : DatabaseObject {
    override val name: String = indexName
    override val type: String = "INDEX"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL constraint (primary key, foreign key, check, unique).
 */
data class PostgresConstraint(
    val constraintName: String,
    val constraintType: String,
    val definition: String
) : DatabaseObject {
    override val name: String = constraintName
    override val type: String = "CONSTRAINT"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL view.
 */
data class PostgresView(
    override val schema: String,
    override val objectName: String,
    val columns: List<PostgresColumn>
) : PostgresObject() {
    override val type: String = "VIEW"
    override val children: List<DatabaseObject> = columns
}

/**
 * Represents a PostgreSQL function.
 */
data class PostgresFunction(
    override val schema: String,
    override val objectName: String,
    val returnType: String,
    val arguments: String,
    val language: String
) : PostgresObject() {
    override val type: String = "FUNCTION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL procedure.
 */
data class PostgresProcedure(
    override val schema: String,
    override val objectName: String,
    val arguments: String,
    val language: String
) : PostgresObject() {
    override val type: String = "PROCEDURE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL sequence.
 */
data class PostgresSequence(
    override val schema: String,
    override val objectName: String,
    val dataType: String,
    val startValue: Long,
    val increment: Long
) : PostgresObject() {
    override val type: String = "SEQUENCE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * PostgreSQL schema comparator that fetches schema via JDBC.
 */
class PostgresSchemaComparator(private val connection: Connection) : SchemaComparator {
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
            val objects = mutableListOf<DatabaseObject>()

            // Fetch tables with columns, indexes, constraints
            fetchTables(connection, objects)
            // Fetch views
            fetchViews(connection, objects)
            // Fetch functions
            fetchFunctions(connection, objects)
            // Fetch procedures
            fetchProcedures(connection, objects)
            // Fetch sequences
            fetchSequences(connection, objects)

            return DatabaseSchema(objects)
        }

        private fun fetchTables(connection: Connection, objects: MutableList<DatabaseObject>) {
            // Fetch columns
            val columnsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            val columnQuery = """
                SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                FROM information_schema.columns
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                ORDER BY table_schema, table_name, ordinal_position
            """.trimIndent()
            connection.createStatement().use { stmt ->
                stmt.executeQuery(columnQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("table_schema")
                        val table = rs.getString("table_name")
                        val column = PostgresColumn(
                            columnName = rs.getString("column_name"),
                            dataType = rs.getString("data_type"),
                            isNullable = rs.getString("is_nullable") == "YES",
                            defaultValue = rs.getString("column_default"),
                            ordinalPosition = rs.getInt("ordinal_position")
                        )
                        columnsByTable.getOrPut(schema to table) { mutableListOf() }.add(column)
                    }
                }
            }

            // Fetch indexes
            val indexesByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresIndex>>()
            val indexQuery = """
                SELECT schemaname, tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
            """.trimIndent()
            connection.createStatement().use { stmt ->
                stmt.executeQuery(indexQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("schemaname")
                        val table = rs.getString("tablename")
                        val indexName = rs.getString("indexname")
                        val indexDef = rs.getString("indexdef")
                        val isUnique = indexDef.contains("UNIQUE", ignoreCase = true)
                        val index = PostgresIndex(
                            indexName = indexName,
                            indexDefinition = indexDef,
                            isUnique = isUnique
                        )
                        indexesByTable.getOrPut(schema to table) { mutableListOf() }.add(index)
                    }
                }
            }

            // Fetch constraints
            val constraintsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresConstraint>>()
            val constraintQuery = """
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
            connection.createStatement().use { stmt ->
                stmt.executeQuery(constraintQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("table_schema")
                        val table = rs.getString("table_name")
                        val constraintName = rs.getString("constraint_name")
                        val constraintType = rs.getString("constraint_type")
                        val columns = rs.getString("columns") ?: ""
                        val definition = when (constraintType) {
                            "PRIMARY KEY" -> "PRIMARY KEY ($columns)"
                            "FOREIGN KEY" -> "FOREIGN KEY ($columns) REFERENCES ..." // simplified
                            "UNIQUE" -> "UNIQUE ($columns)"
                            "CHECK" -> "CHECK (...)" // we could fetch check clause from pg_constraint
                            else -> constraintType
                        }
                        val constraint = PostgresConstraint(
                            constraintName = constraintName,
                            constraintType = constraintType,
                            definition = definition
                        )
                        constraintsByTable.getOrPut(schema to table) { mutableListOf() }.add(constraint)
                    }
                }
            }

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
                        constraints = constraints
                    )
                )
            }
        }

        private fun fetchViews(connection: Connection, objects: MutableList<DatabaseObject>) {
            // Fetch view definitions
            val viewQuery = """
                SELECT table_schema, table_name
                FROM information_schema.views
                WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
            """.trimIndent()
            val views = mutableListOf<Pair<String, String>>()
            connection.createStatement().use { stmt ->
                stmt.executeQuery(viewQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("table_schema")
                        val viewName = rs.getString("table_name")
                        views.add(schema to viewName)
                    }
                }
            }

            // Fetch columns for views
            val columnsByView = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
            if (views.isNotEmpty()) {
                val placeholders = views.joinToString(",") { "('${it.first}', '${it.second}')" }
                val columnQuery = """
                    SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
                    FROM information_schema.columns
                    WHERE (table_schema, table_name) IN ($placeholders)
                    ORDER BY table_schema, table_name, ordinal_position
                """.trimIndent()
                connection.createStatement().use { stmt ->
                    stmt.executeQuery(columnQuery).use { rs ->
                        while (rs.next()) {
                            val schema = rs.getString("table_schema")
                            val viewName = rs.getString("table_name")
                            val column = PostgresColumn(
                                columnName = rs.getString("column_name"),
                                dataType = rs.getString("data_type"),
                                isNullable = rs.getString("is_nullable") == "YES",
                                defaultValue = rs.getString("column_default"),
                                ordinalPosition = rs.getInt("ordinal_position")
                            )
                            columnsByView.getOrPut(schema to viewName) { mutableListOf() }.add(column)
                        }
                    }
                }
            }

            // Create view objects
            for ((schema, viewName) in views) {
                val columns = columnsByView[schema to viewName] ?: emptyList()
                objects.add(
                    PostgresView(
                        schema = schema,
                        objectName = viewName,
                        columns = columns
                    )
                )
            }
        }

        private fun fetchFunctions(connection: Connection, objects: MutableList<DatabaseObject>) {
            // Fetch functions from pg_proc
            val functionQuery = """
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
            connection.createStatement().use { stmt ->
                stmt.executeQuery(functionQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("schema")
                        val functionName = rs.getString("function_name")
                        val returnType = rs.getString("return_type")
                        val arguments = rs.getString("arguments")
                        val language = rs.getString("language")
                        objects.add(
                            PostgresFunction(
                                schema = schema,
                                objectName = functionName,
                                returnType = returnType,
                                arguments = arguments,
                                language = language
                            )
                        )
                    }
                }
            }
        }

        private fun fetchProcedures(connection: Connection, objects: MutableList<DatabaseObject>) {
            // Fetch procedures from pg_proc where prokind = 'p'
            val procedureQuery = """
                SELECT n.nspname AS schema, p.proname AS procedure_name,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                AND p.prokind = 'p'  -- procedures
            """.trimIndent()
            connection.createStatement().use { stmt ->
                stmt.executeQuery(procedureQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("schema")
                        val procedureName = rs.getString("procedure_name")
                        val arguments = rs.getString("arguments")
                        val language = rs.getString("language")
                        objects.add(
                            PostgresProcedure(
                                schema = schema,
                                objectName = procedureName,
                                arguments = arguments,
                                language = language
                            )
                        )
                    }
                }
            }
        }

        private fun fetchSequences(connection: Connection, objects: MutableList<DatabaseObject>) {
            // Fetch sequences from information_schema.sequences
            val sequenceQuery = """
                SELECT sequence_schema, sequence_name, data_type, start_value, increment
                FROM information_schema.sequences
                WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema')
            """.trimIndent()
            connection.createStatement().use { stmt ->
                stmt.executeQuery(sequenceQuery).use { rs ->
                    while (rs.next()) {
                        val schema = rs.getString("sequence_schema")
                        val sequenceName = rs.getString("sequence_name")
                        val dataType = rs.getString("data_type")
                        val startValue = rs.getLong("start_value")
                        val increment = rs.getLong("increment")
                        objects.add(
                            PostgresSequence(
                                schema = schema,
                                objectName = sequenceName,
                                dataType = dataType,
                                startValue = startValue,
                                increment = increment
                            )
                        )
                    }
                }
            }
        }
    }
}