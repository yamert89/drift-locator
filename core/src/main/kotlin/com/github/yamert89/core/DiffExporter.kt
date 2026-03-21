package com.github.yamert89.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports schema and schema differences to a human-readable text file.
 */
object DiffExporter {
    /**
     * Converts a [DatabaseSchema] to a multi-line string representation.
     * The output is sorted alphabetically by object name for consistent comparison.
     * @param schema the database schema to export
     * @param schemaName the name of the schema (e.g., "public") to include in header
     */
    fun toText(schema: DatabaseSchema, schemaName: String? = null): String {
        val sb = StringBuilder()
        sb.appendLine("=== Database Schema ===")
        if (schemaName != null) {
            sb.appendLine("Schema: $schemaName")
        }
        sb.appendLine("Total objects: ${schema.objects.size}")
        sb.appendLine()

        // Sort objects by objectName for consistent output
        val sortedObjects = schema.objects.sortedBy { it.objectName }

        sortedObjects.forEach { obj ->
            appendSchemaObject(sb, obj, 0)
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun appendSchemaObject(
        sb: StringBuilder,
        obj: DatabaseObject,
        indent: Int,
    ) {
        val indentStr = "  ".repeat(indent)
        sb.appendLine("$indentStr[${obj.type}] ${obj.objectName}")

        // Sort children by objectName for consistent output
        val sortedChildren = obj.children.sortedBy { it.objectName }
        sortedChildren.forEach { child ->
            appendSchemaObject(sb, child, indent + 1)
        }
    }

    /**
     * Converts a [SchemaDiff] to a multi-line string representation.
     */
    fun toText(diff: SchemaDiff): String {
        val sb = StringBuilder()
        sb.appendLine("=== Added Objects ===")
        if (diff.added.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            diff.added.forEach { obj: DatabaseObject ->
                appendObject(sb, obj, 0)
            }
        }
        sb.appendLine()

        sb.appendLine("=== Removed Objects ===")
        if (diff.removed.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            diff.removed.forEach { obj: DatabaseObject ->
                appendObject(sb, obj, 0)
            }
        }
        sb.appendLine()

        sb.appendLine("=== Modified Objects ===")
        if (diff.modified.isEmpty()) {
            sb.appendLine("(none)")
        } else {
            diff.modified.forEach { (oldObj: DatabaseObject, newObj: DatabaseObject) ->
                sb.appendLine("Object: ${oldObj.name} (${oldObj.type})")
                sb.appendLine("  --- Old version ---")
                appendObjectDetails(sb, oldObj, 2)
                sb.appendLine("  --- New version ---")
                appendObjectDetails(sb, newObj, 2)
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun appendObject(
        sb: StringBuilder,
        obj: DatabaseObject,
        indent: Int,
    ) {
        val indentStr = " ".repeat(indent)
        sb.appendLine("$indentStr- ${obj.type}: ${obj.objectName}")
        obj.children.forEach { child: DatabaseObject ->
            appendObject(sb, child, indent + 2)
        }
    }

    private fun appendObjectDetails(
        sb: StringBuilder,
        obj: DatabaseObject,
        indent: Int,
    ) {
        val indentStr = " ".repeat(indent)
        sb.appendLine("${indentStr}type: ${obj.type}")
        sb.appendLine("${indentStr}name: ${obj.objectName}")
        if (obj.children.isNotEmpty()) {
            sb.appendLine("${indentStr}children:")
            obj.children.forEach { child: DatabaseObject ->
                appendObjectDetails(sb, child, indent + 2)
            }
        }
    }

    /**
     * Writes the textual representation of the schema to a file.
     */
    fun exportToFile(schema: DatabaseSchema, outputPath: Path) {
        val text = toText(schema)
        Files.writeString(outputPath, text)
    }

    /**
     * Writes the textual representation of the diff to a file.
     */
    fun exportToFile(diff: SchemaDiff, outputPath: Path) {
        val text = toText(diff)
        Files.writeString(outputPath, text)
    }
}
