package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseObject
import com.github.yamert89.core.DatabaseSchema
import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Session
import java.sql.Connection
import kotliquery.Connection as KConnection

private val logger = KotlinLogging.logger {}

/**
 * Fetches PostgreSQL schema snapshots via JDBC.
 */
object PostgresSchemaFetcher {
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
}
