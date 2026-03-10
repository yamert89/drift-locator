package com.github.yamert89.core

/**
 * Represents a database object (table, column, index, etc.)
 */
interface DatabaseObject {
    val name: String
    val type: String
    val children: List<DatabaseObject>
        get() = emptyList()
}

/**
 * Represents a database schema (collection of objects)
 */
data class DatabaseSchema(val objects: List<DatabaseObject>)

/**
 * Compares two schemas and returns differences.
 */
interface SchemaComparator {
    fun compare(source: DatabaseSchema, target: DatabaseSchema): SchemaDiff
}

/**
 * Difference between two schemas.
 */
data class SchemaDiff(
    val added: List<DatabaseObject>,
    val removed: List<DatabaseObject>,
    val modified: List<Pair<DatabaseObject, DatabaseObject>>
)