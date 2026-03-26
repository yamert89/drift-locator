package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class AddConnectionAction : AnAction() {
    private val log = Logger.getInstance("CompareConnectionsAction")
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        log.info("AddConnectionAction triggered")
        val service = DriftLocatorProjectService.getInstance(project)
        val dialog = AddConnectionDialog(project, service.getDefaults(), service.getLastConnection())
        if (dialog.showAndGet()) {

            val connection =
                DatabaseConnection(
                    id = dialog.getConnectionName(),
                    name = dialog.getConnectionName(),
                    host = dialog.getHost(),
                    port = dialog.getPort(),
                    database = dialog.getDatabase(),
                    username = dialog.getUsername(),
                    password = dialog.getPassword(),
                    schema = dialog.getSchema(),
                )
            val passwordStatus = if (connection.password.isNullOrEmpty()) "not set" else "set"
            log.info(
                "Adding connection '${connection.name}' " +
                    "(${connection.host}:${connection.port}/${connection.database}, " +
                    "schema=${connection.schema}, username=${connection.username}, password=$passwordStatus)",
            )

            service.addConnection(connection)
            // Validate connection in background
            validateConnectionInBackground(project, connection, service)
        } else {
            log.debug("Add connection dialog cancelled")
        }
    }
}
