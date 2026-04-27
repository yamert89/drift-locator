package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import kotliquery.Session
import kotliquery.queryOf

internal fun fetchFunctions(
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
        AND p.prokind = 'f'
        """.trimIndent()
    val query = schemaName?.let { queryOf(functionQuery, it) } ?: queryOf(functionQuery)
    session.forEach(query) { row ->
        objects.add(row.toPostgresFunction())
    }
}

internal fun fetchProcedures(
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
        AND p.prokind = 'p'
        """.trimIndent()
    val query = schemaName?.let { queryOf(procedureQuery, it) } ?: queryOf(procedureQuery)
    session.forEach(query) { row ->
        objects.add(row.toPostgresProcedure())
    }
}

internal fun fetchSequences(
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

internal fun fetchTriggers(
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

internal fun fetchEnumTypes(
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

internal fun fetchDomains(
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

internal fun fetchExtensions(
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

internal fun fetchPolicies(
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

internal fun fetchComments(
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

internal fun fetchGrants(
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

internal fun fetchRules(
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
