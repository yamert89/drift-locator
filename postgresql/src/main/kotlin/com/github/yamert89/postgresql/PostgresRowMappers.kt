package com.github.yamert89.postgresql

import kotliquery.Row

fun Row.toPostgresColumn(): PostgresColumn =
    PostgresColumn(
        columnName = string("column_name"),
        dataType = string("data_type"),
        isNullable = string("is_nullable") == "YES",
        defaultValue = stringOrNull("column_default"),
        ordinalPosition = int("ordinal_position"),
    )

fun Row.toPostgresIndex(): PostgresIndex =
    PostgresIndex(
        indexName = string("indexname"),
        indexDefinition = string("indexdef"),
        isUnique = string("indexdef").contains("UNIQUE", ignoreCase = true),
        accessMethod = stringOrNull("access_method"),
        predicate = stringOrNull("predicate"),
        isExpressionBased = boolean("is_expression_based"),
    )

fun Row.toPostgresConstraint(): PostgresConstraint =
    PostgresConstraint(
        constraintName = string("constraint_name"),
        constraintType = string("constraint_type"),
        columns = splitCsv(stringOrNull("columns")),
        referencedTable = stringOrNull("referenced_table"),
        referencedColumns = splitCsv(stringOrNull("referenced_columns")),
        checkClause = stringOrNull("check_clause"),
        definition = string("definition"),
    )

fun Row.toPostgresFunction(): PostgresFunction =
    PostgresFunction(
        schema = string("schema"),
        pgObjectName = string("function_name"),
        identityArguments = string("identity_arguments"),
        returnType = string("return_type"),
        arguments = string("arguments"),
        language = string("language"),
        definition = string("definition"),
    )

fun Row.toPostgresProcedure(): PostgresProcedure =
    PostgresProcedure(
        schema = string("schema"),
        pgObjectName = string("procedure_name"),
        identityArguments = string("identity_arguments"),
        arguments = string("arguments"),
        language = string("language"),
        definition = string("definition"),
    )

fun Row.toPostgresSequence(): PostgresSequence =
    PostgresSequence(
        schema = string("sequence_schema"),
        pgObjectName = string("sequence_name"),
        dataType = string("data_type"),
        startValue = long("start_value"),
        increment = long("increment"),
    )

fun Row.toPostgresTrigger(): PostgresTrigger =
    PostgresTrigger(
        schema = string("schema"),
        pgObjectName = string("trigger_name"),
        tableName = string("table_name"),
        events = splitCsv(stringOrNull("events")).toSet(),
        timing = string("timing"),
        function = string("function_name"),
        definition = string("definition"),
    )

fun Row.toPostgresEnumType(): PostgresEnumType {
    val values = array<String>("enum_values").toList()
    return PostgresEnumType(
        schema = string("schema"),
        pgObjectName = string("enum_name"),
        values = values,
    )
}

fun Row.toPostgresDomain(): PostgresDomain =
    PostgresDomain(
        schema = string("domain_schema"),
        pgObjectName = string("domain_name"),
        baseType = string("data_type"),
        nullable = boolean("is_nullable"),
        defaultValue = stringOrNull("domain_default"),
        checkConstraint = stringOrNull("check_constraint"),
    )

fun Row.toPostgresExtension(): PostgresExtension =
    PostgresExtension(
        pgObjectName = string("extname"),
        schema = string("extnamespace"),
        version = string("extversion"),
    )

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

fun Row.toPostgresComment(): PostgresComment =
    PostgresComment(
        objectType = string("object_type"),
        objectSchema = string("schema"),
        commentedObjectName = string("object_name"),
        comment = string("comment"),
    )

fun Row.toPostgresRule(): PostgresRule =
    PostgresRule(
        schema = string("schema"),
        pgObjectName = string("rule_name"),
        tableName = string("table_name"),
        event = string("event"),
        definition = string("definition"),
    )

fun Row.toPostgresTablespace(): PostgresTablespace =
    PostgresTablespace(
        pgObjectName = string("tablespace_name"),
        location = stringOrNull("location") ?: "",
        options = null,
    )

fun Row.toPostgresRole(): PostgresRole =
    PostgresRole(
        pgObjectName = string("role_name"),
        isSuperuser = boolean("is_superuser"),
        canCreateDb = boolean("can_create_db"),
        canCreateRole = boolean("can_create_role"),
        canLogin = boolean("can_login"),
        validUntil = stringOrNull("valid_until"),
    )

fun Row.toPostgresSchemaObject(): PostgresSchemaObject =
    PostgresSchemaObject(
        schemaName = string("schema_name"),
        owner = string("owner"),
    )

fun Row.toPostgresPublication(): PostgresPublication {
    val tablesArray = array<String>("tables").toList()
    val publishOps = array<String>("publish_ops").toList().filter { it.isNotBlank() }.toSet()
    return PostgresPublication(
        pgObjectName = string("publication_name"),
        tables = tablesArray,
        forAllTables = boolean("for_all_tables"),
        publish = publishOps,
    )
}

fun Row.toPostgresSubscription(): PostgresSubscription {
    val pubNames = array<String>("publication_names").toList()
    return PostgresSubscription(
        pgObjectName = string("subscription_name"),
        connection = string("connection_info_masked"),
        publicationNames = pubNames,
        enabled = boolean("enabled"),
    )
}

fun Row.toPostgresPartition(): PostgresPartition =
    PostgresPartition(
        schema = string("schema"),
        pgObjectName = string("partition_name"),
        parentTable = string("parent_table"),
        partitionKey = stringOrNull("partition_key") ?: "",
        partitionBound = stringOrNull("partition_bound") ?: "",
    )

fun Row.toPostgresAggregate(): PostgresAggregate =
    PostgresAggregate(
        schema = string("schema"),
        pgObjectName = string("aggregate_name"),
        identityArguments = string("identity_arguments"),
        argumentTypes = stringOrNull("argument_types") ?: "",
        stateType = string("state_type"),
        sfunc = string("sfunc"),
        finalfunc = stringOrNull("finalfunc"),
        initcond = stringOrNull("initcond"),
    )

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

fun Row.toPostgresCast(): PostgresCast =
    PostgresCast(
        sourceType = string("source_type"),
        targetType = string("target_type"),
        function = stringOrNull("function_name"),
        context = string("context"),
    )

fun Row.toPostgresFTSConfiguration(): PostgresFTSConfiguration =
    PostgresFTSConfiguration(
        schema = string("schema"),
        pgObjectName = string("config_name"),
        parser = string("parser"),
        dictionaries =
            splitTokenMappings(stringOrNull("dictionary_mappings"))
                .mapValues { (_, dictionaries) -> dictionaries.filter { it.isNotBlank() } },
    )

private fun splitCsv(value: String?): List<String> =
    value?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

private fun splitTokenMappings(value: String?): Map<String, List<String>> =
    value?.split(";")
        ?.mapNotNull { tokenEntry ->
            val parts = tokenEntry.split("=", limit = 2)
            val token = parts.firstOrNull()?.trim().orEmpty()
            if (token.isBlank()) {
                null
            } else {
                token to splitCsv(parts.getOrNull(1))
            }
        }?.toMap()
        ?: emptyMap()
