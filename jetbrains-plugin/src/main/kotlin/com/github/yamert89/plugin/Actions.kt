package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class DriftLocatorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)

        val dialog = SchemaComparisonDialog(project, service)
        if (dialog.showAndGet()) {
            // Perform comparison logic here
            performComparison(project, service, dialog)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun performComparison(
        project: Project,
        unusedService: DriftLocatorProjectService,
        unusedDialog: SchemaComparisonDialog,
    ) {
        // Implement actual comparison using core module
        Messages.showMessageDialog(
            project,
            "Schema comparison would be performed here",
            "Comparison Result",
            Messages.getInformationIcon(),
        )
    }
}

class AddConnectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = AddConnectionDialog(project)
        if (dialog.showAndGet()) {
            val service = DriftLocatorProjectService.getInstance(project)
            val connection = DriftLocatorProjectService.DatabaseConnection(
                id = dialog.getConnectionName(),
                name = dialog.getConnectionName(),
                host = dialog.getHost(),
                port = dialog.getPort(),
                database = dialog.getDatabase(),
                username = dialog.getUsername(),
                password = dialog.getPassword(),
            )
            service.connections[connection.id] = connection
            // Validate connection in background
            validateConnectionInBackground(project, connection)
        }
    }

    private fun validateConnectionInBackground(project: Project, connection: DriftLocatorProjectService.DatabaseConnection) {
        val service = DriftLocatorProjectService.getInstance(project)
        com.github.yamert89.plugin.validateConnectionInBackground(project, connection, service)
    }
}

class DeleteConnectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // todo
    }

    private fun validateConnectionInBackground(project: Project, connection: DriftLocatorProjectService.DatabaseConnection) {
        val service = DriftLocatorProjectService.getInstance(project)
        com.github.yamert89.plugin.validateConnectionInBackground(project, connection, service)
    }
}
