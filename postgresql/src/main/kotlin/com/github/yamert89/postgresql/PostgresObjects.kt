package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject

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
    val constraints: List<PostgresConstraint>,
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
    val ordinalPosition: Int,
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
    val isUnique: Boolean,
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
    val definition: String,
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
    val columns: List<PostgresColumn>,
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
    val language: String,
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
    val language: String,
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
    val increment: Long,
) : PostgresObject() {
    override val type: String = "SEQUENCE"
    override val children: List<DatabaseObject> = emptyList()
}
