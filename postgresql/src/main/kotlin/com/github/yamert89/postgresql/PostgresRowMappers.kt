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
        objectName = string("function_name"),
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
        objectName = string("procedure_name"),
        arguments = string("arguments"),
        language = string("language"),
    )

/**
 * Maps a database row to a [PostgresSequence].
 */
fun Row.toPostgresSequence(): PostgresSequence =
    PostgresSequence(
        schema = string("sequence_schema"),
        objectName = string("sequence_name"),
        dataType = string("data_type"),
        startValue = long("start_value"),
        increment = long("increment"),
    )
