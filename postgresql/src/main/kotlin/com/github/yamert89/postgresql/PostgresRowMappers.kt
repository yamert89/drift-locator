package com.github.yamert89.postgresql

import kotliquery.Row

/**
 * Maps a database row to a [PostgresColumn].
 */
fun Row.toPostgresColumn(): PostgresColumn =
    PostgresColumn(
        columnName = string("column_name"),
        dataType = string("data_type"),
        isNullable = string("is_nullable") == "YES",
        defaultValue = stringOrNull("column_default"),
        ordinalPosition = int("ordinal_position"),
    )

/**
 * Maps a database row to a [PostgresIndex].
 */
fun Row.toPostgresIndex(): PostgresIndex =
    PostgresIndex(
        indexName = string("indexname"),
        indexDefinition = string("indexdef"),
        isUnique = string("indexdef").contains("UNIQUE", ignoreCase = true),
    )

/**
 * Maps a database row to a [PostgresConstraint].
 */
fun Row.toPostgresConstraint(): PostgresConstraint {
    val constraintType = string("constraint_type")
    val columns = stringOrNull("columns") ?: ""
    val definition =
        when (constraintType) {
            "PRIMARY KEY" -> "PRIMARY KEY ($columns)"
            "FOREIGN KEY" -> "FOREIGN KEY ($columns) REFERENCES ..." // simplified
            "UNIQUE" -> "UNIQUE ($columns)"
            "CHECK" -> "CHECK (...)" // we could fetch check clause from pg_constraint
            else -> constraintType
        }
    return PostgresConstraint(
        constraintName = string("constraint_name"),
        constraintType = constraintType,
        definition = definition,
    )
}

/**
 * Maps a database row to a [PostgresFunction].
 */
fun Row.toPostgresFunction(): PostgresFunction =
    PostgresFunction(
        schema = string("schema"),
        pgObjectName = string("function_name"),
        returnType = string("return_type"),
        arguments = string("arguments"),
        language = string("language"),
    )

/**
 * Maps a database row to a [PostgresProcedure].
 */
fun Row.toPostgresProcedure(): PostgresProcedure =
    PostgresProcedure(
        schema = string("schema"),
        pgObjectName = string("procedure_name"),
        arguments = string("arguments"),
        language = string("language"),
    )

/**
 * Maps a database row to a [PostgresSequence].
 */
fun Row.toPostgresSequence(): PostgresSequence =
    PostgresSequence(
        schema = string("sequence_schema"),
        pgObjectName = string("sequence_name"),
        dataType = string("data_type"),
        startValue = long("start_value"),
        increment = long("increment"),
    )

/**
 * Maps a database row to a [PostgresTrigger].
 */
fun Row.toPostgresTrigger(): PostgresTrigger =
    PostgresTrigger(
        schema = string("schema"),
        pgObjectName = string("trigger_name"),
        tableName = string("table_name"),
        event = "", // TODO parse from definition
        timing = "",
        function = "",
        definition = string("definition"),
    )

/**
 * Maps a database row to a [PostgresMaterializedView].
 */
fun Row.toPostgresMaterializedView(): PostgresMaterializedView =
    PostgresMaterializedView(
        schema = string("schema"),
        pgObjectName = string("matview_name"),
        columns = emptyList(), // TODO fetch columns
        definition = null,
    )

/**
 * Maps a database row to a [PostgresEnumType].
 */
fun Row.toPostgresEnumType(): PostgresEnumType {
    val values = array<String>("enum_values")?.toList() ?: emptyList()
    return PostgresEnumType(
        schema = string("schema"),
        pgObjectName = string("enum_name"),
        values = values,
    )
}

/**
 * Maps a database row to a [PostgresDomain].
 */
fun Row.toPostgresDomain(): PostgresDomain =
    PostgresDomain(
        schema = string("domain_schema"),
        pgObjectName = string("domain_name"),
        baseType = string("data_type"),
        nullable = boolean("is_nullable"),
        defaultValue = stringOrNull("domain_default"),
        checkConstraint = stringOrNull("check_constraint"),
    )

/**
 * Maps a database row to a [PostgresExtension].
 */
fun Row.toPostgresExtension(): PostgresExtension =
    PostgresExtension(
        schema = "", // extensions are not schema-bound
        pgObjectName = string("extname"),
        version = string("extversion"),
    )

/**
 * Maps a database row to a [PostgresPolicy].
 */
fun Row.toPostgresPolicy(): PostgresPolicy {
    val commandChar = string("command")
    val command = when (commandChar) {
        "*" -> "ALL"
        "r" -> "SELECT"
        "a" -> "INSERT"
        "w" -> "UPDATE"
        "d" -> "DELETE"
        else -> commandChar
    }
    val roles = array<String>("roles")?.toList() ?: emptyList()
    return PostgresPolicy(
        schema = string("schema"),
        pgObjectName = string("policy_name"),
        tableName = string("table_name"),
        command = command,
        permissive = boolean("permissive"),
        roles = roles,
        usingExpression = stringOrNull("using_expr"),
        withCheckExpression = stringOrNull("with_check_expr"),
    )
}

/**
 * Maps a database row to a [PostgresComment].
 */
fun Row.toPostgresComment(): PostgresComment =
    PostgresComment(
        objectType = string("object_type"),
        objectSchema = string("schema"),
        commentedObjectName = string("object_name"),
        comment = string("comment"),
    )
