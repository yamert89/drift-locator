package com.github.yamert89.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DriftLocatorProjectService(private val project: Project) {
    val connections = ConcurrentHashMap<String, DatabaseConnection>()
    private val connectionChangeListeners = mutableListOf<() -> Unit>()

    data class DatabaseConnection(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String?,
    ) {
        val url: String
            get() = "jdbc:postgresql://$host:$port/$database"
    }

    /**
     * Adds a connection and notifies listeners.
     */
    fun addConnection(connection: DatabaseConnection) {
        connections[connection.id] = connection
        notifyConnectionChanged()
    }

    /**
     * Removes a connection by ID and notifies listeners.
     * @return the removed connection or null if not found
     */
    fun removeConnection(id: String): DatabaseConnection? {
        val removed = connections.remove(id)
        if (removed != null) {
            notifyConnectionChanged()
        }
        return removed
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

    companion object {
        fun getInstance(project: Project): DriftLocatorProjectService = project.service<DriftLocatorProjectService>()
    }
}
