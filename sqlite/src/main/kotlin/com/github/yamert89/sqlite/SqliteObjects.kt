package com.github.yamert89.sqlite

import com.github.yamert89.core.DatabaseObject

sealed class SqliteObject : DatabaseObject {
    abstract val sqliteObjectName: String
    override val name: String get() = sqliteObjectName
    override val objectName: String get() = sqliteObjectName
}

data class SqliteTable(
    override val sqliteObjectName: String,
    val createSql: String,
    val withoutRowId: Boolean,
    val strict: Boolean,
    val columns: List<SqliteColumn>,
    val indexes: List<SqliteIndex>,
    val foreignKeys: List<SqliteForeignKey>,
) : SqliteObject() {
    override val type: String = "TABLE"
    override val children: List<DatabaseObject> = columns + indexes + foreignKeys
}

data class SqliteColumn(
    val columnName: String,
    val dataType: String,
    val isNullable: Boolean,
    val defaultValue: String?,
    val primaryKeyPosition: Int,
    val hidden: Int,
) : DatabaseObject {
    override val name: String = columnName
    override val objectName: String = columnName
    override val type: String = "COLUMN"
}

data class SqliteIndex(
    override val sqliteObjectName: String,
    val tableName: String,
    val isUnique: Boolean,
    val origin: String,
    val isPartial: Boolean,
    val whereClause: String?,
    val definition: String?,
    val columns: List<SqliteIndexColumn>,
) : SqliteObject() {
    override val type: String = "INDEX"
    override val children: List<DatabaseObject> = columns
}

data class SqliteIndexColumn(
    val columnName: String,
    val seqno: Int,
    val cid: Int,
    val isDescending: Boolean,
) : DatabaseObject {
    override val name: String = columnName
    override val objectName: String = columnName
    override val type: String = "INDEX_COLUMN"
}

data class SqliteForeignKey(
    val foreignKeyId: Int,
    val tableName: String,
    val referencedTable: String,
    val fromColumns: List<String>,
    val toColumns: List<String>,
    val onUpdate: String,
    val onDelete: String,
    val match: String,
) : DatabaseObject {
    override val name: String = "$tableName:fk:$foreignKeyId"
    override val objectName: String = "$referencedTable(${fromColumns.joinToString(",")})"
    override val type: String = "FOREIGN_KEY"
}

data class SqliteView(
    override val sqliteObjectName: String,
    val definition: String,
) : SqliteObject() {
    override val type: String = "VIEW"
}

data class SqliteTrigger(
    override val sqliteObjectName: String,
    val tableName: String?,
    val definition: String,
) : SqliteObject() {
    override val type: String = "TRIGGER"
}
