package com.github.yamert89.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class DriftLocatorProjectService(private val project: Project) {
    val connections = ConcurrentHashMap<String, DatabaseConnection>()

    data class DatabaseConnection(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val database: String,
        val username: String,
        val password: String,
    ) {
        val url: String
            get() = "jdbc:postgresql://$host:$port/$database"
    }

    companion object {
        fun getInstance(project: Project): DriftLocatorProjectService = project.service<DriftLocatorProjectService>()
    }
}
