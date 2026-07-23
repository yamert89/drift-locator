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
    val accessMethod: String?,
    val predicate: String?,
    val isExpressionBased: Boolean,
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
    val columns: List<String>,
    val referencedTable: String?,
    val referencedColumns: List<String>,
    val checkClause: String?,
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
    val definition: String?,
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
    val identityArguments: String,
    val returnType: String,
    val arguments: String,
    val language: String,
    val definition: String,
) : PostgresObject() {
    override val name: String get() = "$schema.$objectName"
    override val objectName: String get() = buildSignature(pgObjectName, identityArguments)
    override val type: String = "FUNCTION"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL procedure.
 */
data class PostgresProcedure(
    override val schema: String,
    override val pgObjectName: String,
    val identityArguments: String,
    val arguments: String,
    val language: String,
    val definition: String,
) : PostgresObject() {
    override val name: String get() = "$schema.$objectName"
    override val objectName: String get() = buildSignature(pgObjectName, identityArguments)
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
 * @param event Trigger event: INSERT, UPDATE, DELETE, or TRUNCATE
 * @param timing Trigger timing: BEFORE, AFTER, or INSTEAD OF
 * @param function Function to execute when trigger fires
 */
data class PostgresTrigger(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val events: Set<String>,
    val timing: String,
    val function: String,
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
 * Represents a PostgreSQL extension with its schema.
 */
data class PostgresExtension(
    val pgObjectName: String,
    val schema: String,
    val version: String,
) : DatabaseObject {
    override val name: String = "$schema.$pgObjectName"
    override val type: String = "EXTENSION"
    override val objectName: String = pgObjectName
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL rule.
 * @param event Rule event: SELECT, INSERT, UPDATE, or DELETE
 */
data class PostgresRule(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val event: String,
    val definition: String,
) : PostgresObject() {
    override val type: String = "RULE"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL row-level security policy.
 * @param command Policy command: ALL, SELECT, INSERT, UPDATE, or DELETE
 * @param permissive True for PERMISSIVE, false for RESTRICTIVE
 */
data class PostgresPolicy(
    override val schema: String,
    override val pgObjectName: String,
    val tableName: String,
    val command: String,
    val permissive: Boolean,
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
    /** Object type: TABLE, SEQUENCE, FUNCTION, etc. */
    val objectType: String,
    val objectSchema: String,
    val targetObjectName: String,
    /** Privilege: SELECT, INSERT, UPDATE, DELETE, etc. */
    val privilege: String,
    val isGrantable: Boolean,
) : DatabaseObject {
    override val name: String = "$grantor->$grantee:$objectType:$objectSchema.$targetObjectName:$privilege"
    override val type: String = "GRANT"
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL tablespace (global object, not schema-bound).
 */
data class PostgresTablespace(
    val pgObjectName: String,
    val location: String,
    val options: Map<String, String>?,
) : DatabaseObject {
    override val name: String = pgObjectName
    override val type: String = "TABLESPACE"
    override val objectName: String = pgObjectName
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL role (user or group) - global object, not schema-bound.
 */
data class PostgresRole(
    val pgObjectName: String,
    val isSuperuser: Boolean,
    val canCreateDb: Boolean,
    val canCreateRole: Boolean,
    val canLogin: Boolean,
    val validUntil: String?,
) : DatabaseObject {
    override val name: String = pgObjectName
    override val type: String = "ROLE"
    override val objectName: String = pgObjectName
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL schema (as an object) - stores only schema name without prefix.
 */
data class PostgresSchemaObject(
    val schemaName: String,
    val owner: String,
) : DatabaseObject {
    override val name: String = schemaName
    override val type: String = "SCHEMA"
    override val objectName: String = schemaName
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
 * Represents a PostgreSQL publication (for logical replication) - global object, not schema-bound.
 */
data class PostgresPublication(
    val pgObjectName: String,
    val tables: List<String>,
    val forAllTables: Boolean,
    /** Set of operations: insert, update, delete, truncate */
    val publish: Set<String>,
) : DatabaseObject {
    override val name: String = pgObjectName
    override val type: String = "PUBLICATION"
    override val objectName: String = pgObjectName
    override val children: List<DatabaseObject> = emptyList()
}

/**
 * Represents a PostgreSQL subscription (for logical replication) - global object, not schema-bound.
 */
data class PostgresSubscription(
    val pgObjectName: String,
    val connection: String,
    val publicationNames: List<String>,
    val enabled: Boolean,
) : DatabaseObject {
    override val name: String = pgObjectName
    override val type: String = "SUBSCRIPTION"
    override val objectName: String = pgObjectName
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
    val identityArguments: String,
    val argumentTypes: String,
    val stateType: String,
    val sfunc: String,
    val finalfunc: String?,
    val initcond: String?,
) : PostgresObject() {
    override val name: String get() = "$schema.$objectName"
    override val objectName: String get() = buildSignature(pgObjectName, identityArguments)
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
    override val name: String get() = "$schema.$objectName"
    override val objectName: String
        get() = "$pgObjectName(${leftType ?: "NONE"},${rightType ?: "NONE"})"
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
    /** Cast context: IMPLICIT, ASSIGNMENT, or EXPLICIT */
    val context: String,
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
    val dictionaries: Map<String, List<String>>,
) : PostgresObject() {
    override val type: String = "FTS_CONFIGURATION"
    override val children: List<DatabaseObject> = emptyList()
}

private fun buildSignature(name: String, identityArguments: String): String =
    if (identityArguments.isBlank()) {
        "$name()"
    } else {
        "$name($identityArguments)"
    }

internal fun normalizeProcedureIdentityArguments(identityArguments: String): String =
    identityArguments
        .split(',')
        .joinToString(", ") { argument ->
            argument.trim().replaceFirst(Regex("^IN\\s+", RegexOption.IGNORE_CASE), "")
        }
