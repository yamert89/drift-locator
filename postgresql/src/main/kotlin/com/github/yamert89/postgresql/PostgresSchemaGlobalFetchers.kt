package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import kotliquery.Session
import kotliquery.queryOf

internal fun fetchTablespaces(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchRoles(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchSchemaObjects(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchPublications(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchSubscriptions(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchAggregates(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchOperators(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchCasts(session: Session, objects: MutableList<DatabaseObject>) {
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

internal fun fetchFTSConfigurations(session: Session, objects: MutableList<DatabaseObject>) {
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
