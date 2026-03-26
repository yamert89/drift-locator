package com.github.yamert89.plugin

import com.github.yamert89.plugin.ui.DriftLocatorToolWindowPanel
import com.github.yamert89.plugin.ui.EditConnectionDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

class EditConnectionSchemaAction : AnAction() {
    private val log = Logger.getInstance("EditConnectionSchemaAction")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)
        val toolWindowPanel = getToolWindowPanel(project)

        log.info("EditConnectionSchemaAction triggered")

        // Get selected connection from the tool window panel
        val selectedConnections = toolWindowPanel?.getSelectedConnections() ?: emptyList()

        if (selectedConnections.size != 1) {
            log.warn("Invalid number of connections selected: ${selectedConnections.size}")
            Messages.showErrorDialog(
                project,
                "Please select exactly one connection to edit.",
                "Selection Error",
            )
            return
        }

        val connectionId = selectedConnections[0]
        val connection = service.connections[connectionId]
        if (connection == null) {
            log.warn("Connection '$connectionId' not found")
            Messages.showErrorDialog(project, "Selected connection not found.", "Error")
            return
        }

        val dialog = EditConnectionDialog(project, connection)
        if (dialog.showAndGet()) {
            val newConnection = DatabaseConnection(
                id = dialog.getConnectionName(),
                name = dialog.getConnectionName(),
                host = dialog.getHost(),
                port = dialog.getPort(),
                database = dialog.getDatabase(),
                username = dialog.getUsername(),
                password = dialog.getPassword(),
                schema = dialog.getSchema(),
            )
            try {
                service.updateConnection(connectionId, newConnection)
                log.info("Connection '$connectionId' updated to: ${newConnection.name} (${newConnection.host}:${newConnection.port})")
                // Validate the updated connection in background
                validateConnectionInBackground(project, newConnection, service)
            } catch (e: IllegalArgumentException) {
                log.warn("Failed to update connection: ${e.message}")
                Messages.showErrorDialog(project, e.message, "Error")
            }
        } else {
            log.debug("Edit dialog cancelled")
        }
    }

    private fun getToolWindowPanel(project: Project): DriftLocatorToolWindowPanel? {
        val toolWindow =
            ToolWindowManager
                .getInstance(project)
                .getToolWindow("DriftLocator")
        return toolWindow
            ?.contentManager
            ?.getContent(0)
            ?.component as? DriftLocatorToolWindowPanel
    }
}
