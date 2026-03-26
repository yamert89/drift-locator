package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults
import com.github.yamert89.postgresql.PgMeta
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.io.IOException
import java.util.concurrent.*

@Service(Service.Level.PROJECT)
class DriftLocatorProjectService(private val project: Project) {
    val connections = ConcurrentHashMap<String, DatabaseConnection>()
    private var lastConnection: DatabaseConnection? = null
    private val connectionChangeListeners = mutableListOf<() -> Unit>()
    private val databaseMeta: DatabaseMeta = PgMeta()

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    init {
        loadConnections()
        loadLastConnection()
    }

    /**
     * Adds a connection, saves to disk, and notifies listeners.
     */
    fun addConnection(connection: DatabaseConnection) {
        connections[connection.id] = connection
        lastConnection = connection
        saveConnections()
        saveLastConnection()
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
     * Updates a connection (all fields) and saves to disk.
     * If the connection ID changes (name changed), the old entry is removed and a new one is added.
     * @throws IllegalArgumentException if the new connection ID already exists (different from oldId)
     */
    fun updateConnection(oldId: String, newConnection: DatabaseConnection): DatabaseConnection? {
        val existing = connections[oldId] ?: return null
        // Check if new ID conflicts with another connection
        if (oldId != newConnection.id && connections.containsKey(newConnection.id)) {
            throw IllegalArgumentException("Connection with name '${newConnection.name}' already exists")
        }
        // Remove old entry if ID changed
        if (oldId != newConnection.id) {
            connections.remove(oldId)
        }
        connections[newConnection.id] = newConnection
        // Update lastConnection if it was the old one
        if (lastConnection?.id == oldId) {
            lastConnection = newConnection
            saveLastConnection()
        }
        saveConnections()
        notifyConnectionChanged()
        return newConnection
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

    fun getLastConnection(): DatabaseConnection? = lastConnection

    private fun getConnectionsFile(): File = File(getSystemDir(), "connections.json")

    private fun getLastConnectionFile(): File = File(getSystemDir(), "last-connection.json")

    private fun saveConnections() {
        try {
            val connectionsList: List<DatabaseConnection> = connections.values.toList()
            val jsonString = json.encodeToString(
                ListSerializer(serializer<DatabaseConnection>()),
                connectionsList
            )
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

    private fun loadLastConnection() {
        try {
            val file = getLastConnectionFile()
            if (file.exists()) {
                val jsonString = file.readText()
                lastConnection = json.decodeFromString(serializer<DatabaseConnection>(), jsonString)
            }
        } catch (e: IOException) {
            LOG.warn("Failed to load last connection: ${e.message}")
        }
    }

    private fun saveLastConnection() {
        try {
            val last = lastConnection
            if (last != null) {
                val jsonString = json.encodeToString(serializer<DatabaseConnection>(), last)
                getLastConnectionFile().writeText(jsonString)
            } else {
                getLastConnectionFile().delete()
            }
        } catch (e: IOException) {
            LOG.warn("Failed to save last connection: ${e.message}")
        }
    }

    companion object {
        fun getInstance(project: Project): DriftLocatorProjectService = project.service<DriftLocatorProjectService>()

        private val LOG =
            com.intellij.openapi.diagnostic.Logger
                .getInstance(DriftLocatorProjectService::class.java)
    }
}

