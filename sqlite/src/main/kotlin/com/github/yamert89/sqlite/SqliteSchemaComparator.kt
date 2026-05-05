package com.github.yamert89.sqlite

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.core.SchemaDiff

class SqliteSchemaComparator : SchemaComparator {
    override fun compare(source: DatabaseSchema, target: DatabaseSchema): SchemaDiff {
        val sourceMap = source.objects.associateBy(DatabaseObject::name)
        val targetMap = target.objects.associateBy(DatabaseObject::name)
        val added = target.objects.filter { it.name !in sourceMap }
        val removed = source.objects.filter { it.name !in targetMap }
        val modified =
            sourceMap.mapNotNull { (name, sourceObj) ->
                val targetObj = targetMap[name]
                if (targetObj != null && sourceObj != targetObj) sourceObj to targetObj else null
            }
        return SchemaDiff(added, removed, modified)
    }
}
