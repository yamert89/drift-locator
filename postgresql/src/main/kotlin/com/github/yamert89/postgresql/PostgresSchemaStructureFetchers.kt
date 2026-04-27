package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import kotliquery.Session
import kotliquery.queryOf

internal fun fetchTables(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String? = null,
) {
    val columnsByTable = fetchColumns(session, schemaName)
    val indexesByTable = fetchIndexes(session, schemaName)
    val constraintsByTable = fetchConstraints(session, schemaName)
    val allTables = (columnsByTable.keys + indexesByTable.keys + constraintsByTable.keys).toSet()
    for ((schema, table) in allTables) {
        val columns = columnsByTable[schema to table] ?: emptyList()
        val indexes = indexesByTable[schema to table] ?: emptyList()
        val constraints = constraintsByTable[schema to table] ?: emptyList()
        objects.add(
            PostgresTable(
                schema = schema,
                pgObjectName = table,
                columns = columns,
                indexes = indexes,
                constraints = constraints,
            ),
        )
    }
}

internal fun fetchColumns(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresColumn>> {
    val columnsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
    val schemaFilter = schemaName?.let { "AND table_schema = ?" } ?: ""
    val columnQuery =
        """
        SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
        FROM information_schema.columns
        WHERE table_schema NOT IN ('pg_catalog', 'information_schema', 'pg_toast') $schemaFilter
        ORDER BY table_schema, table_name, ordinal_position
        """.trimIndent()
    val query = schemaName?.let { queryOf(columnQuery, it) } ?: queryOf(columnQuery)
    session.forEach(query) { row ->
        val schema = row.string("table_schema")
        val table = row.string("table_name")
        val column = row.toPostgresColumn()
        columnsByTable.getOrPut(schema to table) { mutableListOf() }.add(column)
    }
    return columnsByTable
}

internal fun fetchIndexes(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresIndex>> {
    val indexesByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresIndex>>()
    val schemaFilter = schemaName?.let { "AND ns.nspname = ?" } ?: ""
    val indexQuery =
        """
        SELECT ns.nspname AS schemaname,
               tbl.relname AS tablename,
               idx.relname AS indexname,
               pg_get_indexdef(i.indexrelid) AS indexdef,
               am.amname AS access_method,
               pg_get_expr(i.indpred, i.indrelid) AS predicate,
               i.indexprs IS NOT NULL AS is_expression_based
        FROM pg_index i
        JOIN pg_class idx ON idx.oid = i.indexrelid
        JOIN pg_class tbl ON tbl.oid = i.indrelid
        JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
        JOIN pg_am am ON am.oid = idx.relam
        WHERE ns.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') $schemaFilter
        """.trimIndent()
    val query = schemaName?.let { queryOf(indexQuery, it) } ?: queryOf(indexQuery)
    session.forEach(query) { row ->
        val schema = row.string("schemaname")
        val table = row.string("tablename")
        val index = row.toPostgresIndex()
        indexesByTable.getOrPut(schema to table) { mutableListOf() }.add(index)
    }
    return indexesByTable
}

internal fun fetchConstraints(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresConstraint>> {
    val constraintsByTable = mutableMapOf<Pair<String, String>, MutableList<PostgresConstraint>>()
    val schemaFilter = schemaName?.let { "AND src_ns.nspname = ?" } ?: ""
    val constraintQuery =
        """
        SELECT src_ns.nspname AS table_schema,
               src.relname AS table_name,
               con.conname AS constraint_name,
               CASE con.contype
                   WHEN 'p' THEN 'PRIMARY KEY'
                   WHEN 'f' THEN 'FOREIGN KEY'
                   WHEN 'u' THEN 'UNIQUE'
                   WHEN 'c' THEN 'CHECK'
                   ELSE con.contype::text
               END AS constraint_type,
               (
                   SELECT string_agg(att.attname, ',' ORDER BY key_positions.ordinality)
                   FROM unnest(con.conkey) WITH ORDINALITY AS key_positions(attnum, ordinality)
                   JOIN pg_attribute att
                     ON att.attrelid = con.conrelid
                    AND att.attnum = key_positions.attnum
               ) AS columns,
               CASE
                   WHEN con.confrelid = 0 THEN NULL
                   ELSE ref_ns.nspname || '.' || ref.relname
               END AS referenced_table,
               (
                   SELECT string_agg(att.attname, ',' ORDER BY key_positions.ordinality)
                   FROM unnest(con.confkey) WITH ORDINALITY AS key_positions(attnum, ordinality)
                   JOIN pg_attribute att
                     ON att.attrelid = con.confrelid
                    AND att.attnum = key_positions.attnum
               ) AS referenced_columns,
               CASE
                   WHEN con.contype = 'c' THEN pg_get_constraintdef(con.oid, true)
                   ELSE NULL
               END AS check_clause,
               pg_get_constraintdef(con.oid, true) AS definition
        FROM pg_constraint con
        JOIN pg_class src ON src.oid = con.conrelid
        JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
        LEFT JOIN pg_class ref ON ref.oid = con.confrelid
        LEFT JOIN pg_namespace ref_ns ON ref_ns.oid = ref.relnamespace
        WHERE src_ns.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') $schemaFilter
        """.trimIndent()
    val query = schemaName?.let { queryOf(constraintQuery, it) } ?: queryOf(constraintQuery)
    session.forEach(query) { row ->
        val schema = row.string("table_schema")
        val table = row.string("table_name")
        val constraint = row.toPostgresConstraint()
        constraintsByTable.getOrPut(schema to table) { mutableListOf() }.add(constraint)
    }
    return constraintsByTable
}

internal fun fetchViews(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String? = null,
) {
    val schemaFilter = schemaName?.let { "AND table_schema = ?" } ?: ""
    val viewQuery =
        """
        SELECT views.table_schema,
               views.table_name,
               pg_get_viewdef((quote_ident(views.table_schema) || '.' || quote_ident(views.table_name))::regclass, true) AS definition
        FROM information_schema.views views
        WHERE table_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
        """.trimIndent()
    val viewQueryObj = schemaName?.let { queryOf(viewQuery, it) } ?: queryOf(viewQuery)
    val views = mutableListOf<Triple<String, String, String?>>()
    session.forEach(viewQueryObj) { row ->
        val schema = row.string("table_schema")
        val viewName = row.string("table_name")
        views.add(Triple(schema, viewName, row.stringOrNull("definition")))
    }

    val columnsByView = mutableMapOf<Pair<String, String>, MutableList<PostgresColumn>>()
    if (views.isNotEmpty()) {
        val placeholders = views.joinToString(",") { "('${it.first}', '${it.second}')" }
        val schemaFilter2 = schemaName?.let { "AND table_schema = ?" } ?: ""
        val columnQuery =
            """
            SELECT table_schema, table_name, column_name, data_type, is_nullable, column_default, ordinal_position
            FROM information_schema.columns
            WHERE (table_schema, table_name) IN ($placeholders) $schemaFilter2
            ORDER BY table_schema, table_name, ordinal_position
            """.trimIndent()
        val columnQueryObj = schemaName?.let { queryOf(columnQuery, it) } ?: queryOf(columnQuery)
        session.forEach(columnQueryObj) { row ->
            val schema = row.string("table_schema")
            val viewName = row.string("table_name")
            val column = row.toPostgresColumn()
            columnsByView.getOrPut(schema to viewName) { mutableListOf() }.add(column)
        }
    }

    for ((schema, viewName, definition) in views) {
        val columns = columnsByView[schema to viewName] ?: emptyList()
        objects.add(
            PostgresView(
                schema = schema,
                pgObjectName = viewName,
                columns = columns,
                definition = definition,
            ),
        )
    }
}

internal fun fetchMaterializedViews(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String? = null,
) {
    val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
    val matViewQuery =
        """
        SELECT n.nspname AS schema,
               c.relname AS matview_name,
               pg_get_viewdef(c.oid, true) AS definition
        FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        WHERE c.relkind = 'm'
          AND n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
        """.trimIndent()
    val query = schemaName?.let { queryOf(matViewQuery, it) } ?: queryOf(matViewQuery)
    val matViews = mutableListOf<Triple<String, String, String?>>()
    session.forEach(query) { row ->
        matViews.add(Triple(row.string("schema"), row.string("matview_name"), row.stringOrNull("definition")))
    }

    for ((schema, name, definition) in matViews) {
        val columnQuery =
            """
            SELECT a.attname AS column_name,
                   pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type,
                   CASE WHEN a.attnotnull THEN 'NO' ELSE 'YES' END AS is_nullable,
                   pg_get_expr(d.adbin, d.adrelid) AS column_default,
                   a.attnum AS ordinal_position
            FROM pg_attribute a
            JOIN pg_class c ON a.attrelid = c.oid
            JOIN pg_namespace n ON c.relnamespace = n.oid
            LEFT JOIN pg_attrdef d ON a.attrelid = d.adrelid AND a.attnum = d.adnum
            WHERE c.relname = ?
              AND n.nspname = ?
              AND a.attnum > 0
              AND NOT a.attisdropped
            ORDER BY a.attnum
            """.trimIndent()
        val columns = mutableListOf<PostgresColumn>()
        session.forEach(queryOf(columnQuery, name, schema)) { row ->
            columns.add(row.toPostgresColumn())
        }
        objects.add(
            PostgresMaterializedView(
                schema = schema,
                pgObjectName = name,
                columns = columns,
                definition = definition,
            ),
        )
    }
}

internal fun fetchForeignTables(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String? = null,
) {
    val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
    val foreignTableQuery =
        """
        SELECT n.nspname AS schema,
               c.relname AS table_name,
               s.srvname AS server_name
        FROM pg_class c
        JOIN pg_namespace n ON c.relnamespace = n.oid
        JOIN pg_foreign_table ft ON c.oid = ft.ftrelid
        JOIN pg_foreign_server s ON ft.ftserver = s.oid
        WHERE c.relkind = 'f'
          AND n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
        """.trimIndent()
    val query = schemaName?.let { queryOf(foreignTableQuery, it) } ?: queryOf(foreignTableQuery)
    val foreignTables = mutableListOf<Triple<String, String, String>>()
    session.forEach(query) { row ->
        foreignTables.add(Triple(row.string("schema"), row.string("table_name"), row.string("server_name")))
    }

    for ((schema, tableName, serverName) in foreignTables) {
        val columnQuery =
            """
            SELECT column_name, data_type, is_nullable, column_default, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """.trimIndent()
        val columns = mutableListOf<PostgresColumn>()
        session.forEach(queryOf(columnQuery, schema, tableName)) { row ->
            columns.add(row.toPostgresColumn())
        }
        objects.add(
            PostgresForeignTable(
                schema = schema,
                pgObjectName = tableName,
                columns = columns,
                serverName = serverName,
                options = null,
            ),
        )
    }
}

internal fun fetchPartitions(
    session: Session,
    objects: MutableList<DatabaseObject>,
    schemaName: String? = null,
) {
    val schemaFilter = schemaName?.let { "AND pn.nspname = ?" } ?: ""
    val partitionQuery =
        """
        SELECT pn.nspname AS schema,
               c.relname AS partition_name,
               pn2.nspname || '.' || p.relname AS parent_table,
               pg_get_partkeydef(p.oid) AS partition_key,
               pg_get_expr(c.relpartbound, c.oid) AS partition_bound
        FROM pg_class c
        JOIN pg_namespace pn ON c.relnamespace = pn.oid
        JOIN pg_inherits i ON c.oid = i.inhrelid
        JOIN pg_class p ON i.inhparent = p.oid
        JOIN pg_namespace pn2 ON p.relnamespace = pn2.oid
        WHERE c.relkind = 'r'
          AND pn.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
        """.trimIndent()
    val query = schemaName?.let { queryOf(partitionQuery, it) } ?: queryOf(partitionQuery)
    session.forEach(query) { row ->
        objects.add(row.toPostgresPartition())
    }
}
