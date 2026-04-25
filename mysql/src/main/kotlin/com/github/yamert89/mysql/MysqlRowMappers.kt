package com.github.yamert89.mysql

import kotliquery.Row

fun Row.toMysqlColumn(): MysqlColumn =
    MysqlColumn(
        columnName = string("column_name"),
        dataType = string("data_type"),
        columnType = string("column_type"),
        isNullable = string("is_nullable") == "YES",
        defaultValue = stringOrNull("column_default"),
        ordinalPosition = int("ordinal_position"),
        extra = stringOrNull("extra"),
        generationExpression = stringOrNull("generation_expression"),
    )

fun Row.toMysqlView(columns: List<MysqlColumn>): MysqlView =
    MysqlView(
        schema = string("table_schema"),
        mysqlObjectName = string("table_name"),
        definition = stringOrNull("view_definition"),
        checkOption = stringOrNull("check_option"),
        columns = columns,
    )

fun Row.toMysqlTrigger(): MysqlTrigger =
    MysqlTrigger(
        schema = string("trigger_schema"),
        mysqlObjectName = string("trigger_name"),
        tableName = string("event_object_table"),
        event = string("event_manipulation"),
        timing = string("action_timing"),
        statement = string("action_statement"),
    )

fun Row.toMysqlEvent(): MysqlEvent =
    MysqlEvent(
        schema = string("event_schema"),
        mysqlObjectName = string("event_name"),
        definition = stringOrNull("event_definition"),
        intervalValue = stringOrNull("interval_value"),
        intervalField = stringOrNull("interval_field"),
        status = string("status"),
    )

fun Row.toMysqlPartition(): MysqlPartition =
    MysqlPartition(
        schema = string("table_schema"),
        mysqlObjectName = string("partition_name"),
        tableName = string("table_name"),
        method = stringOrNull("partition_method"),
        expression = stringOrNull("partition_expression"),
        description = stringOrNull("partition_description"),
    )

fun Row.toMysqlSchemaObject(): MysqlSchemaObject =
    MysqlSchemaObject(
        schemaName = string("schema_name"),
        charset = stringOrNull("default_character_set_name"),
        collation = stringOrNull("default_collation_name"),
    )

fun Row.toMysqlUser(): MysqlUser =
    MysqlUser(
        user = string("user"),
        host = string("host"),
        accountLocked = stringOrNull("account_locked")?.equals("Y", ignoreCase = true),
    )

fun Row.toMysqlTablespace(): MysqlTablespace =
    MysqlTablespace(
        tablespaceName = string("tablespace_name"),
        engine = stringOrNull("engine"),
    )
