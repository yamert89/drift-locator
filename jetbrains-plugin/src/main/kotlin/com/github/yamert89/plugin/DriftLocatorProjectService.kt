package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults
import com.github.yamert89.postgresql.PgMeta
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.*

@Service(Service.Level.PROJECT)
class DriftLocatorProjectService(private val project: Project) {
    val connections = ConcurrentHashMap<String, DatabaseConnection>()
    private val connectionChangeListeners = mutableListOf<() -> Unit>()
    private val databaseMeta: DatabaseMeta = PgMeta()

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    init {
        loadConnections()
    }

    /**
     * Adds a connection, saves to disk, and notifies listeners.
     */
    fun addConnection(connection: DatabaseConnection) {
        connections[connection.id] = connection
        saveConnections()
        notifyConnectionChanged()
    }

    /**
     * Removes a connection by ID, saves to disk, and notifies listeners.
     * @return the removed connection or null if not found
     */
    fun removeConnection(id: String): DatabaseConnection? {
        val removed = connections.remove(id)
        if (removed != null) {
            saveConnections()
            notifyConnectionChanged()
        }
        return removed
    }

    /**
     * Updates the schema for a connection and saves to disk.
     */
    fun updateConnectionSchema(id: String, schema: String): DatabaseConnection? {
        val existing = connections[id] ?: return null
        val updated = existing.copy(schema = schema)
        connections[id] = updated
        saveConnections()
        notifyConnectionChanged()
        return updated
    }

    /**
     * Registers a listener to be called when connections change.
     */
    fun addConnectionChangeListener(listener: () -> Unit) {
        connectionChangeListeners.add(listener)
    }

    /**
     * Unregisters a connection change listener.
     */
    fun removeConnectionChangeListener(listener: () -> Unit) {
        connectionChangeListeners.remove(listener)
    }

    /**
     * Notifies all registered listeners that connections have changed.
     */
    fun notifyConnectionChanged() {
        connectionChangeListeners.forEach { it() }
    }

    /**
     * Returns the directory for storing plugin system data (.driftLocator/system).
     */
    fun getSystemDir(): File {
        val baseDir = File(project.basePath, ".driftLocator")
        val systemDir = File(baseDir, "system")
        systemDir.mkdirs()
        return systemDir
    }

    fun getDefaults(): Defaults = databaseMeta.getDefaults()

    private fun getConnectionsFile(): File = File(getSystemDir(), "connections.json")

    private fun saveConnections() {
        try {
            val connectionsList: List<DatabaseConnection> = connections.values.toList()
            val jsonString = json.encodeToString<List<DatabaseConnection>>(connectionsList)
            getConnectionsFile().writeText(jsonString)
        } catch (e: IOException) {
            LOG.warn("Failed to save connections: ${e.message}")
        }
    }

    private fun loadConnections() {
        try {
            val file = getConnectionsFile()
            if (file.exists()) {
                val jsonString = file.readText()
                val connectionsList = json.decodeFromString<List<DatabaseConnection>>(jsonString)
                connections.clear()
                connectionsList.forEach { connections[it.id] = it }
                LOG.info("Loaded ${connections.size} connections from disk")
            }
        } catch (e: IOException) {
            LOG.warn("Failed to load connections: ${e.message}")
        }
    }

    companion object {
        fun getInstance(project: Project): DriftLocatorProjectService = project.service<DriftLocatorProjectService>()

        private val LOG =
            com.intellij.openapi.diagnostic.Logger
                .getInstance(DriftLocatorProjectService::class.java)
    }
}

