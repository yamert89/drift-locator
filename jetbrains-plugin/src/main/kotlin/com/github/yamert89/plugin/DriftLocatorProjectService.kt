package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults
import com.github.yamert89.postgresql.PgMeta
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
     * Password is stored securely via PasswordSafe only if savePassword is true.
     */
    fun addConnection(connection: DatabaseConnection) {
        connections[connection.id] = connection
        lastConnection = connection
        // Save password to secure storage only if savePassword is true (in background thread)
        ApplicationManager.getApplication().executeOnPooledThread {
            if (connection.savePassword) {
                PasswordStorage.savePassword(connection.id, connection.password)
            } else {
                PasswordStorage.removePassword(connection.id)
            }
        }
        saveConnections()
        saveLastConnection()
        notifyConnectionChanged()
    }

    /**
     * Updates the validation status of a connection.
     * @return true if the connection was found and updated, false otherwise
     */
    fun updateConnectionValidationStatus(id: String, isValid: Boolean): Boolean {
        val connection = connections[id] ?: return false
        if (connection.isValid != isValid) {
            connections[id] = connection.copy(isValid = isValid)
            saveConnections()
            notifyConnectionChanged()
        }
        return true
    }

    /**
     * Removes a connection by ID, saves to disk, and notifies listeners.
     * Also removes the associated password from secure storage.
     * @return the removed connection or null if not found
     */
    fun removeConnection(id: String): DatabaseConnection? {
        val removed = connections.remove(id)
        if (removed != null) {
            // Remove password from secure storage (in background thread)
            ApplicationManager.getApplication().executeOnPooledThread {
                PasswordStorage.removePassword(id)
            }
            saveConnections()
            notifyConnectionChanged()
        }
        return removed
    }

    /**
     * Updates a connection (all fields) and saves to disk.
     * If the connection ID changes (name changed), the old entry is removed and a new one is added.
     * Password is updated in secure storage only if savePassword is true.
     * @throws IllegalArgumentException if the new connection ID already exists (different from oldId)
     */
    fun updateConnection(oldId: String, newConnection: DatabaseConnection): DatabaseConnection? {
        val existing = connections[oldId] ?: return null
        // Check if new ID conflicts with another connection
        if (oldId != newConnection.id && connections.containsKey(newConnection.id)) {
            throw IllegalArgumentException("Connection with name '${newConnection.name}' already exists")
        }
        // Remove old entry and its password if ID changed (in background thread)
        if (oldId != newConnection.id) {
            connections.remove(oldId)
            ApplicationManager.getApplication().executeOnPooledThread {
                PasswordStorage.removePassword(oldId)
            }
        }
        connections[newConnection.id] = newConnection
        // Save password to secure storage only if savePassword is true (in background thread)
        ApplicationManager.getApplication().executeOnPooledThread {
            if (newConnection.savePassword) {
                PasswordStorage.savePassword(newConnection.id, newConnection.password)
            } else {
                PasswordStorage.removePassword(newConnection.id)
            }
        }
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
        val systemDir = getSystemDirPath()
        systemDir.mkdirs()
        return systemDir
    }

    fun getDefaults(): Defaults = databaseMeta.getDefaults()

    fun getLastConnection(): DatabaseConnection? = lastConnection

    private fun getSystemDirPath(): File {
        val baseDir = File(project.basePath, ".driftLocator")
        return File(baseDir, "system")
    }

    private fun getConnectionsFile(createDirectory: Boolean = false): File {
        val systemDir = if (createDirectory) getSystemDir() else getSystemDirPath()
        return File(systemDir, "connections.json")
    }

    private fun getLastConnectionFile(createDirectory: Boolean = false): File {
        val systemDir = if (createDirectory) getSystemDir() else getSystemDirPath()
        return File(systemDir, "last-connection.json")
    }

    private fun saveConnections() {
        try {
            // Create copies without passwords for JSON serialization
            val connectionsList =
                connections.values.map { conn ->
                    conn.copy().apply { password = null }
                }
            val jsonString =
                json.encodeToString(
                    ListSerializer(serializer<DatabaseConnection>()),
                    connectionsList,
                )
            getConnectionsFile(createDirectory = true).writeText(jsonString)
        } catch (e: IOException) {
            LOG.warn("Failed to save connections: ${e.message}")
        }
    }

    private fun loadConnections() {
        try {
            val file = getConnectionsFile()
            if (file.exists()) {
                val jsonString = file.readText()
                var migrated = false

                // Check if JSON contains passwords (old format) and migrate them (in background thread)
                val jsonElement = json.parseToJsonElement(jsonString)
                if (jsonElement is kotlinx.serialization.json.JsonArray) {
                    val passwordsToMigrate = mutableListOf<Pair<String, String>>()
                    jsonElement.forEach { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content
                        val password = obj["password"]?.jsonPrimitive?.content
                        if (id != null && !password.isNullOrBlank()) {
                            passwordsToMigrate.add(id to password)
                            migrated = true
                        }
                    }
                    if (passwordsToMigrate.isNotEmpty()) {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            passwordsToMigrate.forEach { (id, password) ->
                                PasswordStorage.savePassword(id, password)
                            }
                        }
                    }
                }

                if (migrated) {
                    LOG.info("Migrated passwords from JSON to secure storage")
                    // Re-save connections without passwords
                    saveConnections()
                }

                val connectionsList = json.decodeFromString<List<DatabaseConnection>>(jsonString)
                connections.clear()
                connectionsList.forEach { conn ->
                    connections[conn.id] = conn
                }
                // Restore passwords from secure storage in background thread
                ApplicationManager.getApplication().executeOnPooledThread {
                    connectionsList.forEach { conn ->
                        if (conn.savePassword) {
                            conn.password = PasswordStorage.getPassword(conn.id)
                        }
                    }
                }
                LOG.info("Loaded ${connections.size} connections from disk")
            }
        } catch (e: IOException) {
            LOG.warn("Failed to load connections: ${e.message}")
        } catch (e: Exception) {
            LOG.warn("Failed to parse connections: ${e.message}")
        }
    }

    private fun loadLastConnection() {
        try {
            val file = getLastConnectionFile()
            if (file.exists()) {
                val jsonString = file.readText()
                lastConnection = json.decodeFromString(serializer<DatabaseConnection>(), jsonString)
                // Restore password from secure storage in background thread
                lastConnection?.let { conn ->
                    if (conn.savePassword) {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            conn.password = PasswordStorage.getPassword(conn.id)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            LOG.warn("Failed to load last connection: ${e.message}")
        }
    }

    private fun saveLastConnection() {
        try {
            val last = lastConnection
            if (last != null) {
                // Save without password (password stays in secure storage)
                val connectionWithoutPassword = last.copy().apply { password = null }
                val jsonString = json.encodeToString(serializer<DatabaseConnection>(), connectionWithoutPassword)
                getLastConnectionFile(createDirectory = true).writeText(jsonString)
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
