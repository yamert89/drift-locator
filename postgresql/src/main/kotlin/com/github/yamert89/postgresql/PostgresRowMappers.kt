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
fun Row.toPostgresTrigger(): PostgresTrigger {
    val def = string("definition")
    // Parse trigger definition to extract timing, event, and function
    // Example: CREATE TRIGGER name BEFORE INSERT ON table EXECUTE FUNCTION func()
    val timing =
        when {
            def.contains(" BEFORE ", ignoreCase = true) -> "BEFORE"
            def.contains(" AFTER ", ignoreCase = true) -> "AFTER"
            def.contains(" INSTEAD OF ", ignoreCase = true) -> "INSTEAD OF"
            else -> ""
        }
    val event =
        when {
            def.contains(" INSERT ", ignoreCase = true) -> "INSERT"
            def.contains(" UPDATE ", ignoreCase = true) -> "UPDATE"
            def.contains(" DELETE ", ignoreCase = true) -> "DELETE"
            def.contains(" TRUNCATE ", ignoreCase = true) -> "TRUNCATE"
            else -> ""
        }
    val functionRegex = "EXECUTE\\s+(?:FUNCTION|PROCEDURE)\\s+(\\w+)".toRegex(RegexOption.IGNORE_CASE)
    val function = functionRegex.find(def)?.groupValues?.get(1) ?: ""
    return PostgresTrigger(
        schema = string("schema"),
        pgObjectName = string("trigger_name"),
        tableName = string("table_name"),
        event = event,
        timing = timing,
        function = function,
        definition = def,
    )
}

/**
 * Maps a database row to a [PostgresEnumType].
 */
fun Row.toPostgresEnumType(): PostgresEnumType {
    val values = array<String>("enum_values").toList()
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
        pgObjectName = string("extname"),
        schema = string("extnamespace"),
        version = string("extversion"),
    )

/**
 * Maps a database row to a [PostgresPolicy].
 */
fun Row.toPostgresPolicy(): PostgresPolicy {
    val command =
        when (val commandChar = string("command")) {
            "*" -> "ALL"
            "r" -> "SELECT"
            "a" -> "INSERT"
            "w" -> "UPDATE"
            "d" -> "DELETE"
            else -> commandChar
        }
    val roles = array<String>("roles").toList()
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

/**
 * Maps a database row to a [PostgresRule].
 */
fun Row.toPostgresRule(): PostgresRule =
    PostgresRule(
        schema = string("schema"),
        pgObjectName = string("rule_name"),
        tableName = string("table_name"),
        event = string("event"),
        definition = string("definition"),
    )

/**
 * Maps a database row to a [PostgresTablespace].
 */
fun Row.toPostgresTablespace(): PostgresTablespace =
    PostgresTablespace(
        pgObjectName = string("tablespace_name"),
        location = stringOrNull("location") ?: "",
        options = null,
    )

/**
 * Maps a database row to a [PostgresRole].
 */
fun Row.toPostgresRole(): PostgresRole =
    PostgresRole(
        pgObjectName = string("role_name"),
        isSuperuser = boolean("is_superuser"),
        canCreateDb = boolean("can_create_db"),
        canCreateRole = boolean("can_create_role"),
        canLogin = boolean("can_login"),
        validUntil = stringOrNull("valid_until"),
    )

/**
 * Maps a database row to a [PostgresSchemaObject].
 */
fun Row.toPostgresSchemaObject(): PostgresSchemaObject =
    PostgresSchemaObject(
        schemaName = string("schema_name"),
        owner = string("owner"),
    )

/**
 * Maps a database row to a [PostgresPublication].
 */
fun Row.toPostgresPublication(): PostgresPublication {
    val tablesArray = array<String>("tables").toList()
    val publishOps = array<String>("publish_ops").toList().toSet()
    return PostgresPublication(
        pgObjectName = string("publication_name"),
        tables = tablesArray,
        forAllTables = boolean("for_all_tables"),
        publish = publishOps,
    )
}

/**
 * Maps a database row to a [PostgresSubscription].
 */
fun Row.toPostgresSubscription(): PostgresSubscription {
    val pubNames = array<String>("publication_names").toList()
    return PostgresSubscription(
        pgObjectName = string("subscription_name"),
        connection = string("connection_info"),
        publicationNames = pubNames,
        enabled = boolean("enabled"),
    )
}

/**
 * Maps a database row to a [PostgresPartition].
 */
fun Row.toPostgresPartition(): PostgresPartition =
    PostgresPartition(
        schema = string("schema"),
        pgObjectName = string("partition_name"),
        parentTable = string("parent_table"),
        partitionKey = stringOrNull("partition_key") ?: "",
        partitionBound = stringOrNull("partition_bound") ?: "",
    )

/**
 * Maps a database row to a [PostgresAggregate].
 */
fun Row.toPostgresAggregate(): PostgresAggregate =
    PostgresAggregate(
        schema = string("schema"),
        pgObjectName = string("aggregate_name"),
        argumentTypes = stringOrNull("argument_types") ?: "",
        stateType = string("state_type"),
        sfunc = string("sfunc"),
        finalfunc = stringOrNull("finalfunc"),
        initcond = stringOrNull("initcond"),
    )

/**
 * Maps a database row to a [PostgresOperator].
 */
fun Row.toPostgresOperator(): PostgresOperator =
    PostgresOperator(
        schema = string("schema"),
        pgObjectName = string("operator_name"),
        leftType = stringOrNull("left_type"),
        rightType = stringOrNull("right_type"),
        function = string("function_name"),
        commutator = stringOrNull("commutator"),
        negator = stringOrNull("negator"),
    )

/**
 * Maps a database row to a [PostgresCast].
 */
fun Row.toPostgresCast(): PostgresCast =
    PostgresCast(
        sourceType = string("source_type"),
        targetType = string("target_type"),
        function = stringOrNull("function_name"),
        context = string("context"),
    )

/**
 * Maps a database row to a [PostgresFTSConfiguration].
 */
fun Row.toPostgresFTSConfiguration(): PostgresFTSConfiguration =
    PostgresFTSConfiguration(
        schema = string("schema"),
        pgObjectName = string("config_name"),
        parser = string("parser"),
        dictionaries = emptyMap(),
    )
