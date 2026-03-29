package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject

sealed class PostgresObject : DatabaseObject {
    abstract val schema: String
    abstract val pgObjectName: String
    override val name: String get() = "$schema.$pgObjectName"
    override val objectName: String get() = pgObjectName
}

/**
 * Represents a PostgreSQL table.
 */
data class PostgresTable(
    override val schema: String,
    override val pgObjectName: String,
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
    override val pgObjectName: String,
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
    override val pgObjectName: String,
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
    override val pgObjectName: String,
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
    override val pgObjectName: String,
    val dataType: String,
    val startValue: Long,
    val increment: Long,
) : PostgresObject() {
    override val type: String = "SEQUENCE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL trigger.
 */
data class PostgresTrigger(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val event: String,  // INSERT, UPDATE, DELETE, TRUNCATE
    val timing: String, // BEFORE, AFTER, INSTEAD OF
    val function: String, // function to execute
    val definition: String,
) : PostgresObject() {
    override val type: String = "TRIGGER"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL materialized view.
 */
data class PostgresMaterializedView(
    override val schema: String,
    override val pgObjectName: String,
    val columns: List<PostgresColumn>,
    val definition: String?,
) : PostgresObject() {
    override val type: String = "MATERIALIZED_VIEW"
    override val children: List<DatabaseObject> = columns
}

/**
 * Represents a PostgreSQL enum type.
 */
data class PostgresEnumType(
    override val schema: String,
    override val pgObjectName: String,
    val values: List<String>,
) : PostgresObject() {
    override val type: String = "ENUM"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL domain (domain type).
 */
data class PostgresDomain(
    override val schema: String,
    override val pgObjectName: String,
    val baseType: String,
    val nullable: Boolean,
    val defaultValue: String?,
    val checkConstraint: String?,
) : PostgresObject() {
    override val type: String = "DOMAIN"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL extension.
 */
data class PostgresExtension(
    override val schema: String,
    override val pgObjectName: String,
    val version: String,
) : PostgresObject() {
    override val type: String = "EXTENSION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL rule.
 */
data class PostgresRule(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val event: String, // SELECT, INSERT, UPDATE, DELETE
    val definition: String,
) : PostgresObject() {
    override val type: String = "RULE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL row-level security policy.
 */
data class PostgresPolicy(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val command: String, // ALL, SELECT, INSERT, UPDATE, DELETE
    val permissive: Boolean, // true for PERMISSIVE, false for RESTRICTIVE
    val roles: List<String>,
    val usingExpression: String?,
    val withCheckExpression: String?,
) : PostgresObject() {
    override val type: String = "POLICY"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL grant (privilege).
 */
data class PostgresGrant(
    val grantor: String,
    val grantee: String,
    val objectType: String, // TABLE, SEQUENCE, FUNCTION, etc.
    val objectSchema: String,
    val targetObjectName: String,
    val privilege: String, // SELECT, INSERT, UPDATE, DELETE, etc.
    val isGrantable: Boolean,
) : DatabaseObject {
    override val name: String = "$grantor->$grantee:$objectType:$objectSchema.$targetObjectName:$privilege"
    override val type: String = "GRANT"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL tablespace.
 */
data class PostgresTablespace(
    override val schema: String,
    override val pgObjectName: String,
    val location: String,
    val options: Map<String, String>?,
) : PostgresObject() {
    override val type: String = "TABLESPACE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL role (user or group).
 */
data class PostgresRole(
    override val schema: String,
    override val pgObjectName: String,
    val isSuperuser: Boolean,
    val canCreateDb: Boolean,
    val canCreateRole: Boolean,
    val canLogin: Boolean,
    val validUntil: String?,
) : PostgresObject() {
    override val type: String = "ROLE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL schema (as an object).
 */
data class PostgresSchemaObject(
    override val schema: String,
    override val pgObjectName: String,
    val owner: String,
) : PostgresObject() {
    override val type: String = "SCHEMA"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL foreign table.
 */
data class PostgresForeignTable(
    override val schema: String,
    override val pgObjectName: String,
    val columns: List<PostgresColumn>,
    val serverName: String,
    val options: Map<String, String>?,
) : PostgresObject() {
    override val type: String = "FOREIGN_TABLE"
    override val children: List<DatabaseObject> = columns
}

/**
 * Represents a PostgreSQL publication (for logical replication).
 */
data class PostgresPublication(
    override val schema: String,
    override val pgObjectName: String,
    val tables: List<String>,
    val forAllTables: Boolean,
    val publish: Set<String>, // insert, update, delete, truncate
) : PostgresObject() {
    override val type: String = "PUBLICATION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL subscription (for logical replication).
 */
data class PostgresSubscription(
    override val schema: String,
    override val pgObjectName: String,
    val connection: String,
    val publicationNames: List<String>,
    val enabled: Boolean,
) : PostgresObject() {
    override val type: String = "SUBSCRIPTION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL comment on an object.
 */
data class PostgresComment(
    val objectType: String,
    val objectSchema: String,
    val commentedObjectName: String,
    val comment: String,
) : DatabaseObject {
    override val name: String = "$objectType:$objectSchema.$commentedObjectName"
    override val type: String = "COMMENT"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL partition (child table of a partitioned table).
 */
data class PostgresPartition(
    override val schema: String,
    override val pgObjectName: String,
    val parentTable: String,
    val partitionKey: String,
    val partitionBound: String,
) : PostgresObject() {
    override val type: String = "PARTITION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL aggregate function.
 */
data class PostgresAggregate(
    override val schema: String,
    override val pgObjectName: String,
    val argumentTypes: String,
    val stateType: String,
    val sfunc: String,
    val finalfunc: String?,
    val initcond: String?,
) : PostgresObject() {
    override val type: String = "AGGREGATE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL operator.
 */
data class PostgresOperator(
    override val schema: String,
    override val pgObjectName: String,
    val leftType: String?,
    val rightType: String?,
    val function: String,
    val commutator: String?,
    val negator: String?,
) : PostgresObject() {
    override val type: String = "OPERATOR"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL cast (type conversion).
 */
data class PostgresCast(
    val sourceType: String,
    val targetType: String,
    val function: String?,
    val context: String, // IMPLICIT, ASSIGNMENT, EXPLICIT
) : DatabaseObject {
    override val name: String = "$sourceType->$targetType"
    override val type: String = "CAST"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL full-text search configuration.
 */
data class PostgresFTSConfiguration(
    override val schema: String,
    override val pgObjectName: String,
    val parser: String,
    val dictionaries: Map<String, String>,
) : PostgresObject() {
    override val type: String = "FTS_CONFIGURATION"
    override val children: List<DatabaseObject> = emptyList()
}
