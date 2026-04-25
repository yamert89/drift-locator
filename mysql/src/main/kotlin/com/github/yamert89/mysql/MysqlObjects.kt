package com.github.yamert89.mysql

import com.github.yamert89.core.DatabaseObject

sealed class MysqlObject : DatabaseObject {
    abstract val schema: String
    abstract val mysqlObjectName: String
    override val name: String get() = "$schema.$mysqlObjectName"
    override val objectName: String get() = mysqlObjectName
}

data class MysqlTable(
    override val schema: String,
    override val mysqlObjectName: String,
    val engine: String?,
    val collation: String?,
    val columns: List<MysqlColumn>,
    val indexes: List<MysqlIndex>,
    val constraints: List<MysqlConstraint>,
) : MysqlObject() {
    override val type: String = "TABLE"
    override val children: List<DatabaseObject> = columns + indexes + constraints
}

data class MysqlColumn(
    val columnName: String,
    val dataType: String,
    val columnType: String,
    val isNullable: Boolean,
    val defaultValue: String?,
    val ordinalPosition: Int,
    val extra: String?,
    val generationExpression: String?,
) : DatabaseObject {
    override val name: String = columnName
    override val type: String = "COLUMN"
}

data class MysqlIndex(
    val indexName: String,
    val columns: List<String>,
    val isUnique: Boolean,
    val indexType: String,
) : DatabaseObject {
    override val name: String = indexName
    override val type: String = "INDEX"
}

data class MysqlConstraint(
    val constraintName: String,
    val constraintType: String,
    val tableName: String,
    val columns: List<String>,
    val referencedTable: String?,
    val referencedColumns: List<String>,
    val checkClause: String?,
) : DatabaseObject {
    override val name: String = constraintName
    override val type: String = "CONSTRAINT"
}

data class MysqlView(
    override val schema: String,
    override val mysqlObjectName: String,
    val definition: String?,
    val checkOption: String?,
    val columns: List<MysqlColumn>,
) : MysqlObject() {
    override val type: String = "VIEW"
    override val children: List<DatabaseObject> = columns
}

data class MysqlRoutine(
    override val schema: String,
    override val mysqlObjectName: String,
    val routineType: String,
    val returnType: String?,
    val parameters: List<MysqlRoutineParameter>,
    val body: String?,
) : MysqlObject() {
    override val type: String = routineType
    override val children: List<DatabaseObject> = parameters
}

data class MysqlRoutineParameter(
    val parameterName: String,
    val mode: String?,
    val dataType: String,
    val ordinalPosition: Int,
) : DatabaseObject {
    override val name: String = parameterName
    override val type: String = "PARAMETER"
}

data class MysqlTrigger(
    override val schema: String,
    override val mysqlObjectName: String,
    val tableName: String,
    val event: String,
    val timing: String,
    val statement: String,
) : MysqlObject() {
    override val type: String = "TRIGGER"
}

data class MysqlEvent(
    override val schema: String,
    override val mysqlObjectName: String,
    val definition: String?,
    val intervalValue: String?,
    val intervalField: String?,
    val status: String,
) : MysqlObject() {
    override val type: String = "EVENT"
}

data class MysqlPartition(
    override val schema: String,
    override val mysqlObjectName: String,
    val tableName: String,
    val method: String?,
    val expression: String?,
    val description: String?,
) : MysqlObject() {
    override val type: String = "PARTITION"
}

data class MysqlSchemaObject(
    val schemaName: String,
    val charset: String?,
    val collation: String?,
) : DatabaseObject {
    override val name: String = schemaName
    override val type: String = "SCHEMA"
    override val objectName: String = schemaName
}

data class MysqlGrant(
    val grantee: String,
    val objectType: String,
    val targetObjectName: String,
    val privilege: String,
    val isGrantable: Boolean,
) : DatabaseObject {
    override val name: String = "$grantee:$objectType:$targetObjectName:$privilege"
    override val type: String = "GRANT"
}

data class MysqlUser(
    val user: String,
    val host: String,
    val accountLocked: Boolean?,
) : DatabaseObject {
    override val name: String = "$user@$host"
    override val type: String = "USER"
}

data class MysqlTablespace(
    val tablespaceName: String,
    val engine: String?,
) : DatabaseObject {
    override val name: String = tablespaceName
    override val type: String = "TABLESPACE"
    override val objectName: String = tablespaceName
}
