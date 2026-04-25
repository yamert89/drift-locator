package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.SchemaComparator
import com.github.yamert89.core.SchemaDiff
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Session
import kotliquery.queryOf
import java.sql.Connection
import kotliquery.Connection as KConnection

private val logger = KotlinLogging.logger {}

/**
 * PostgreSQL schema comparator that fetches schema via JDBC.
 */
class PostgresSchemaComparator : SchemaComparator {
    override fun compare(source: DatabaseSchema, target: DatabaseSchema): SchemaDiff {
        val sourceMap = source.objects.associateBy { it.name }
        val targetMap = target.objects.associateBy { it.name }

        val added = target.objects.filter { it.name !in sourceMap }
        val removed = source.objects.filter { it.name !in targetMap }
        val modified = mutableListOf<Pair<DatabaseObject, DatabaseObject>>()

        for ((name, sourceObj) in sourceMap) {
            val targetObj = targetMap[name]
            if (targetObj != null && sourceObj != targetObj) {
                modified.add(sourceObj to targetObj)
            }
        }

        return SchemaDiff(added, removed, modified)
    }

    companion object {
        /**
         * Fetches all schemas from a PostgreSQL database (excluding system schemas).
         */
        fun fetchSchema(connection: Connection): DatabaseSchema {
            return fetchSchema(connection, null)
        }

        /**
         * Fetches a specific schema from a PostgreSQL database.
         * @param connection JDBC connection
         * @param schemaName schema name to fetch, or null to fetch all non-system schemas
         */
        fun fetchSchema(connection: Connection, schemaName: String?): DatabaseSchema {
            logger.info { "Fetching schema ${schemaName ?: "(all non-system)"}" }
            val session = Session(KConnection(connection))
            val objects = mutableListOf<DatabaseObject>()
            fetchSafely("tables") { fetchTables(session, objects, schemaName) }
            fetchSafely("views") { fetchViews(session, objects, schemaName) }
            fetchSafely("functions") { fetchFunctions(session, objects, schemaName) }
            fetchSafely("procedures") { fetchProcedures(session, objects, schemaName) }
            fetchSafely("sequences") { fetchSequences(session, objects, schemaName) }
            fetchSafely("triggers") { fetchTriggers(session, objects, schemaName) }
            fetchSafely("materialized views") { fetchMaterializedViews(session, objects, schemaName) }
            fetchSafely("enum types") { fetchEnumTypes(session, objects, schemaName) }
            fetchSafely("domains") { fetchDomains(session, objects, schemaName) }
            fetchSafely("extensions") { fetchExtensions(session, objects, schemaName) }
            fetchSafely("policies") { fetchPolicies(session, objects, schemaName) }
            fetchSafely("comments") { fetchComments(session, objects, schemaName) }
            fetchSafely("grants") { fetchGrants(session, objects, schemaName) }
            fetchSafely("rules") { fetchRules(session, objects, schemaName) }
            fetchSafely("foreign tables") { fetchForeignTables(session, objects, schemaName) }
            fetchSafely("partitions") { fetchPartitions(session, objects, schemaName) }
            if (schemaName == null) {
                fetchSafely("schemas") { fetchSchemaObjects(session, objects) }
            }
            // Global objects (only when fetching all schemas)
            if (schemaName == null) {
                fetchSafely("tablespaces") { fetchTablespaces(session, objects) }
                fetchSafely("roles") { fetchRoles(session, objects) }
                fetchSafely("publications") { fetchPublications(session, objects) }
                fetchSafely("subscriptions") { fetchSubscriptions(session, objects) }
                fetchSafely("aggregates") { fetchAggregates(session, objects) }
                fetchSafely("operators") { fetchOperators(session, objects) }
                fetchSafely("casts") { fetchCasts(session, objects) }
                fetchSafely("FTS configurations") { fetchFTSConfigurations(session, objects) }
            }
            logger.info { "Schema fetch complete: ${objects.size} total objects" }
            return DatabaseSchema(objects)
        }

        @Suppress("TooGenericExceptionCaught")
        private inline fun fetchSafely(objectType: String, fetcher: () -> Unit) {
            try {
                logger.debug { "Fetching $objectType..." }
                fetcher()
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to fetch $objectType, skipping: ${e.message}" }
            }
        }

        /** Fetch tables with columns, indexes, constraints */
        private fun fetchTables(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val columnsByTable = fetchColumns(session, schemaName)
            val indexesByTable = fetchIndexes(session, schemaName)
            val constraintsByTable = fetchConstraints(session, schemaName)

            // Create table objects
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

        private fun fetchColumns(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresColumn>> {
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

        private fun fetchIndexes(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresIndex>> {
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

        private fun fetchConstraints(session: Session, schemaName: String? = null): Map<Pair<String, String>, List<PostgresConstraint>> {
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

        private fun fetchViews(
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

            // Fetch columns for views
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

            // Create view objects
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

        private fun fetchFunctions(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val functionQuery =
                """
                SELECT n.nspname AS schema, p.proname AS function_name,
                       pg_get_function_identity_arguments(p.oid) AS identity_arguments,
                       pg_get_function_result(p.oid) AS return_type,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language,
                       pg_get_functiondef(p.oid) AS definition
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                AND p.prokind = 'f'  -- functions
                """.trimIndent()
            val query = schemaName?.let { queryOf(functionQuery, it) } ?: queryOf(functionQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresFunction())
            }
        }

        private fun fetchProcedures(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val procedureQuery =
                """
                SELECT n.nspname AS schema, p.proname AS procedure_name,
                       pg_get_function_identity_arguments(p.oid) AS identity_arguments,
                       pg_get_function_arguments(p.oid) AS arguments,
                       l.lanname AS language,
                       pg_get_functiondef(p.oid) AS definition
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_language l ON p.prolang = l.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                AND p.prokind = 'p'  -- procedures
                """.trimIndent()
            val query = schemaName?.let { queryOf(procedureQuery, it) } ?: queryOf(procedureQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresProcedure())
            }
        }

        private fun fetchSequences(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND sequence_schema = ?" } ?: ""
            val sequenceQuery =
                """
                SELECT sequence_schema, sequence_name, data_type, start_value, increment
                FROM information_schema.sequences
                WHERE sequence_schema NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(sequenceQuery, it) } ?: queryOf(sequenceQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresSequence())
            }
        }

        private fun fetchTriggers(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val triggerQuery =
                """
                SELECT n.nspname AS schema,
                       t.tgname AS trigger_name,
                       c.relname AS table_name,
                       CASE
                           WHEN (t.tgtype & 2) <> 0 THEN 'BEFORE'
                           WHEN (t.tgtype & 64) <> 0 THEN 'INSTEAD OF'
                           ELSE 'AFTER'
                       END AS timing,
                       array_to_string(
                           array_remove(
                               ARRAY[
                                   CASE WHEN (t.tgtype & 4) <> 0 THEN 'INSERT' END,
                                   CASE WHEN (t.tgtype & 8) <> 0 THEN 'DELETE' END,
                                   CASE WHEN (t.tgtype & 16) <> 0 THEN 'UPDATE' END,
                                   CASE WHEN (t.tgtype & 32) <> 0 THEN 'TRUNCATE' END
                               ],
                               NULL
                           ),
                           ','
                       ) AS events,
                       fn_ns.nspname || '.' || fn.proname AS function_name,
                       pg_get_triggerdef(t.oid) AS definition
                FROM pg_trigger t
                JOIN pg_class c ON t.tgrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                JOIN pg_proc fn ON fn.oid = t.tgfoid
                JOIN pg_namespace fn_ns ON fn_ns.oid = fn.pronamespace
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                  AND NOT t.tgisinternal
                """.trimIndent()
            val query = schemaName?.let { queryOf(triggerQuery, it) } ?: queryOf(triggerQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresTrigger())
            }
        }

        private fun fetchMaterializedViews(
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

            // Fetch columns for each materialized view using pg_catalog
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

        private fun fetchEnumTypes(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val enumQuery =
                """
                SELECT n.nspname AS schema, t.typname AS enum_name,
                       array_agg(e.enumlabel ORDER BY e.enumsortorder) AS enum_values
                FROM pg_type t
                JOIN pg_enum e ON t.oid = e.enumtypid
                JOIN pg_namespace n ON t.typnamespace = n.oid
                WHERE t.typtype = 'e'
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                GROUP BY n.nspname, t.typname
                """.trimIndent()
            val query = schemaName?.let { queryOf(enumQuery, it) } ?: queryOf(enumQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresEnumType())
            }
        }

        private fun fetchDomains(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val domainQuery =
                """
                SELECT n.nspname AS domain_schema,
                       t.typname AS domain_name,
                       pg_catalog.format_type(t.typbasetype, t.typtypmod) AS data_type,
                       NOT t.typnotnull AS is_nullable,
                       pg_get_expr(t.typdefaultbin, t.oid) AS domain_default,
                       (SELECT string_agg(con.conname || ' ' || pg_get_constraintdef(con.oid), '; ' ORDER BY con.conname)
                        FROM pg_constraint con
                        WHERE con.contypid = t.oid AND con.contype = 'c') AS check_constraint
                FROM pg_type t
                JOIN pg_namespace n ON t.typnamespace = n.oid
                WHERE t.typtype = 'd'
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(domainQuery, it) } ?: queryOf(domainQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresDomain())
            }
        }

        private fun fetchExtensions(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "WHERE n.nspname = ?" } ?: ""
            val extensionQuery =
                """
                SELECT e.extname, e.extversion, n.nspname AS extnamespace
                FROM pg_extension e
                JOIN pg_namespace n ON e.extnamespace = n.oid
                $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(extensionQuery, it) } ?: queryOf(extensionQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresExtension())
            }
        }

        private fun fetchPolicies(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val policyQuery =
                """
                SELECT n.nspname AS schema, c.relname AS table_name, p.polname AS policy_name,
                       p.polpermissive AS permissive,
                       p.polcmd AS command,
                       COALESCE(array_agg(r.rolname) FILTER (WHERE r.oid IS NOT NULL), ARRAY['public']) AS roles,
                       pg_get_expr(p.polqual, p.polrelid) AS using_expr,
                       pg_get_expr(p.polwithcheck, p.polrelid) AS with_check_expr
                FROM pg_policy p
                JOIN pg_class c ON p.polrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                LEFT JOIN LATERAL unnest(p.polroles) AS role_oid ON true
                LEFT JOIN pg_roles r ON r.oid = role_oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                GROUP BY n.nspname, c.relname, p.polname, p.polpermissive, p.polcmd, p.polqual, p.polwithcheck, p.polrelid
                """.trimIndent()
            val query = schemaName?.let { queryOf(policyQuery, it) } ?: queryOf(policyQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresPolicy())
            }
        }

        private fun fetchComments(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val commentQuery =
                """
                SELECT n.nspname AS schema,
                       c.relname AS object_name,
                       CASE c.relkind
                           WHEN 'r' THEN 'TABLE'
                           WHEN 'v' THEN 'VIEW'
                           WHEN 'm' THEN 'MATERIALIZED_VIEW'
                           WHEN 'i' THEN 'INDEX'
                           WHEN 'S' THEN 'SEQUENCE'
                           WHEN 'f' THEN 'FOREIGN_TABLE'
                           ELSE 'OTHER'
                       END AS object_type,
                       obj_description(c.oid) AS comment
                FROM pg_class c
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                  AND obj_description(c.oid) IS NOT NULL
                UNION ALL
                SELECT n.nspname AS schema,
                       p.proname AS object_name,
                       CASE p.prokind
                           WHEN 'f' THEN 'FUNCTION'
                           WHEN 'p' THEN 'PROCEDURE'
                           ELSE 'ROUTINE'
                       END AS object_type,
                       obj_description(p.oid) AS comment
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                  AND obj_description(p.oid) IS NOT NULL
                """.trimIndent()
            val query = schemaName?.let { queryOf(commentQuery, it, it) } ?: queryOf(commentQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresComment())
            }
        }

        private fun fetchGrants(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter =
                schemaName?.let { "AND table_schema = ?" }
                    ?: "AND table_schema NOT IN ('pg_catalog', 'information_schema')"
            val grantQuery =
                """
                SELECT grantor,
                       grantee,
                       'TABLE' AS object_type,
                       table_schema AS object_schema,
                       table_name AS object_name,
                       privilege_type AS privilege,
                       is_grantable = 'YES' AS is_grantable
                FROM information_schema.role_table_grants
                WHERE 1 = 1 $schemaFilter
                """.trimIndent()
            val query = schemaName?.let { queryOf(grantQuery, it) } ?: queryOf(grantQuery)
            session.forEach(query) { row ->
                objects.add(
                    PostgresGrant(
                        grantor = row.string("grantor"),
                        grantee = row.string("grantee"),
                        objectType = row.string("object_type"),
                        objectSchema = row.string("object_schema"),
                        targetObjectName = row.string("object_name"),
                        privilege = row.string("privilege"),
                        isGrantable = row.boolean("is_grantable"),
                    ),
                )
            }
        }

        private fun fetchRules(
            session: Session,
            objects: MutableList<DatabaseObject>,
            schemaName: String? = null,
        ) {
            val schemaFilter = schemaName?.let { "AND n.nspname = ?" } ?: ""
            val ruleQuery =
                """
                SELECT n.nspname AS schema,
                       r.rulename AS rule_name,
                       c.relname AS table_name,
                       CASE r.ev_type
                           WHEN '1' THEN 'SELECT'
                           WHEN '2' THEN 'UPDATE'
                           WHEN '3' THEN 'INSERT'
                           WHEN '4' THEN 'DELETE'
                           ELSE r.ev_type::text
                       END AS event,
                       pg_get_ruledef(r.oid) AS definition
                FROM pg_rewrite r
                JOIN pg_class c ON r.ev_class = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') $schemaFilter
                  AND r.rulename NOT LIKE '_RETURN'
                """.trimIndent()
            val query = schemaName?.let { queryOf(ruleQuery, it) } ?: queryOf(ruleQuery)
            session.forEach(query) { row ->
                objects.add(row.toPostgresRule())
            }
        }

        private fun fetchTablespaces(session: Session, objects: MutableList<DatabaseObject>) {
            val tablespaceQuery =
                """
                SELECT spcname AS tablespace_name,
                       pg_tablespace_location(oid) AS location
                FROM pg_tablespace
                WHERE spcname NOT LIKE 'pg_%'
                """.trimIndent()
            session.forEach(queryOf(tablespaceQuery)) { row ->
                objects.add(row.toPostgresTablespace())
            }
        }

        private fun fetchRoles(session: Session, objects: MutableList<DatabaseObject>) {
            val roleQuery =
                """
                SELECT rolname AS role_name,
                       rolsuper AS is_superuser,
                       rolcreatedb AS can_create_db,
                       rolcreaterole AS can_create_role,
                       rolcanlogin AS can_login,
                       rolvaliduntil::text AS valid_until
                FROM pg_roles
                WHERE rolname NOT LIKE 'pg_%'
                  AND rolname != 'postgres'
                """.trimIndent()
            session.forEach(queryOf(roleQuery)) { row ->
                objects.add(row.toPostgresRole())
            }
        }

        private fun fetchSchemaObjects(session: Session, objects: MutableList<DatabaseObject>) {
            val schemaQuery =
                """
                SELECT n.nspname AS schema_name,
                       pg_catalog.pg_get_userbyid(n.nspowner) AS owner
                FROM pg_namespace n
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
                  AND n.nspname NOT LIKE 'pg_temp_%'
                  AND n.nspname NOT LIKE 'pg_toast_temp_%'
                """.trimIndent()
            session.forEach(queryOf(schemaQuery)) { row ->
                objects.add(row.toPostgresSchemaObject())
            }
        }

        private fun fetchForeignTables(
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
                foreignTables.add(
                    Triple(
                        row.string("schema"),
                        row.string("table_name"),
                        row.string("server_name"),
                    ),
                )
            }

            // Fetch columns for foreign tables
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

        private fun fetchPublications(session: Session, objects: MutableList<DatabaseObject>) {
            val publicationQuery =
                """
                SELECT p.pubname AS publication_name,
                       p.puballtables AS for_all_tables,
                       array_remove(ARRAY[
                           CASE WHEN p.pubinsert THEN 'insert' END,
                           CASE WHEN p.pubupdate THEN 'update' END,
                           CASE WHEN p.pubdelete THEN 'delete' END,
                           CASE WHEN p.pubtruncate THEN 'truncate' END
                       ], NULL) AS publish_ops,
                       COALESCE(
                           ARRAY_AGG(
                               CASE WHEN pr.prrelid IS NOT NULL 
                               THEN pn.nspname || '.' || pc.relname 
                               END
                           ) FILTER (WHERE pr.prrelid IS NOT NULL),
                           ARRAY[]::text[]
                       ) AS tables
                FROM pg_publication p
                LEFT JOIN pg_publication_rel pr ON p.oid = pr.prpubid
                LEFT JOIN pg_class pc ON pr.prrelid = pc.oid
                LEFT JOIN pg_namespace pn ON pc.relnamespace = pn.oid
                GROUP BY p.pubname, p.puballtables, p.pubinsert, p.pubupdate, p.pubdelete, p.pubtruncate
                """.trimIndent()
            session.forEach(queryOf(publicationQuery)) { row ->
                objects.add(row.toPostgresPublication())
            }
        }

        private fun fetchSubscriptions(session: Session, objects: MutableList<DatabaseObject>) {
            val subscriptionQuery =
                """
                SELECT subname AS subscription_name,
                       regexp_replace(
                           regexp_replace(subconninfo, '(?i)password=[^[:space:]]+', 'password=****', 'g'),
                           '(?i)passfile=[^[:space:]]+',
                           'passfile=****',
                           'g'
                       ) AS connection_info_masked,
                       subpublications AS publication_names,
                       subenabled AS enabled
                FROM pg_subscription
                """.trimIndent()
            session.forEach(queryOf(subscriptionQuery)) { row ->
                objects.add(row.toPostgresSubscription())
            }
        }

        private fun fetchPartitions(
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

        private fun fetchAggregates(session: Session, objects: MutableList<DatabaseObject>) {
            val aggregateQuery =
                """
                SELECT n.nspname AS schema,
                       p.proname AS aggregate_name,
                       pg_get_function_identity_arguments(p.oid) AS identity_arguments,
                       pg_get_function_arguments(p.oid) AS argument_types,
                       t.typname AS state_type,
                       sf.proname AS sfunc,
                       ff.proname AS finalfunc,
                       a.agginitval AS initcond
                FROM pg_aggregate a
                JOIN pg_proc p ON a.aggfnoid = p.oid
                JOIN pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_type t ON a.aggtranstype = t.oid
                JOIN pg_proc sf ON a.aggtransfn = sf.oid
                LEFT JOIN pg_proc ff ON a.aggfinalfn = ff.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            session.forEach(queryOf(aggregateQuery)) { row ->
                objects.add(row.toPostgresAggregate())
            }
        }

        private fun fetchOperators(session: Session, objects: MutableList<DatabaseObject>) {
            val operatorQuery =
                """
                SELECT n.nspname AS schema,
                       o.oprname AS operator_name,
                       lt.typname AS left_type,
                       rt.typname AS right_type,
                       p.proname AS function_name,
                       com.oprname AS commutator,
                       neg.oprname AS negator
                FROM pg_operator o
                JOIN pg_namespace n ON o.oprnamespace = n.oid
                LEFT JOIN pg_type lt ON o.oprleft = lt.oid
                LEFT JOIN pg_type rt ON o.oprright = rt.oid
                JOIN pg_proc p ON o.oprcode = p.oid
                LEFT JOIN pg_operator com ON o.oprcom = com.oid
                LEFT JOIN pg_operator neg ON o.oprnegate = neg.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            session.forEach(queryOf(operatorQuery)) { row ->
                objects.add(row.toPostgresOperator())
            }
        }

        private fun fetchCasts(session: Session, objects: MutableList<DatabaseObject>) {
            val castQuery =
                """
                SELECT st.typname AS source_type,
                       tt.typname AS target_type,
                       p.proname AS function_name,
                       CASE c.castcontext
                           WHEN 'e' THEN 'EXPLICIT'
                           WHEN 'a' THEN 'ASSIGNMENT'
                           WHEN 'i' THEN 'IMPLICIT'
                       END AS context
                FROM pg_cast c
                JOIN pg_type st ON c.castsource = st.oid
                JOIN pg_type tt ON c.casttarget = tt.oid
                LEFT JOIN pg_proc p ON c.castfunc = p.oid
                WHERE st.typnamespace NOT IN (SELECT oid FROM pg_namespace WHERE nspname IN ('pg_catalog', 'information_schema'))
                   OR tt.typnamespace NOT IN (SELECT oid FROM pg_namespace WHERE nspname IN ('pg_catalog', 'information_schema'))
                """.trimIndent()
            session.forEach(queryOf(castQuery)) { row ->
                objects.add(row.toPostgresCast())
            }
        }

        private fun fetchFTSConfigurations(session: Session, objects: MutableList<DatabaseObject>) {
            val ftsQuery =
                """
                SELECT n.nspname AS schema,
                       c.cfgname AS config_name,
                       p.prsname AS parser,
                       (
                           SELECT string_agg(
                               token_alias || '=' || dictionary_names,
                               '; ' ORDER BY token_alias
                           )
                           FROM (
                               SELECT m.alias AS token_alias,
                                      array_to_string(array_agg(d.dictname ORDER BY map.mapseqno), ',') AS dictionary_names
                               FROM pg_ts_config_map map
                               JOIN pg_ts_dict d ON d.oid = map.mapdict
                               JOIN ts_token_type(c.cfgparser) m ON m.tokid = map.maptokentype
                               WHERE map.mapcfg = c.oid
                               GROUP BY m.alias
                           ) mappings
                       ) AS dictionary_mappings
                FROM pg_ts_config c
                JOIN pg_namespace n ON c.cfgnamespace = n.oid
                JOIN pg_ts_parser p ON c.cfgparser = p.oid
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                """.trimIndent()
            session.forEach(queryOf(ftsQuery)) { row ->
                objects.add(row.toPostgresFTSConfiguration())
            }
        }
    }
}
