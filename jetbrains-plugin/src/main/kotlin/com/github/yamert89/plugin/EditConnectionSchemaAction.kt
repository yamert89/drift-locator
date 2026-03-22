package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

class EditConnectionSchemaAction : AnAction() {
    private val LOG = Logger.getInstance("EditConnectionSchemaAction")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)
        val toolWindowPanel = getToolWindowPanel(project)

        LOG.info("EditConnectionSchemaAction triggered")

        // Get selected connection from the tool window panel
        val selectedConnections = toolWindowPanel?.getSelectedConnections() ?: emptyList()

        if (selectedConnections.size != 1) {
            LOG.warn("Invalid number of connections selected: ${selectedConnections.size}")
            Messages.showErrorDialog(
                project,
                "Please select exactly one connection to edit its schema.",
                "Selection Error",
            )
            return
        }

        val connectionId = selectedConnections[0]
        val dialog = ConnectionSchemaDialog(project, service, connectionId)
        if (dialog.showAndGet()) {
            val schemaName = dialog.getSchemaName()
            service.updateConnectionSchema(connectionId, schemaName)
            LOG.info("Schema updated for connection '$connectionId' to: $schemaName")
        } else {
            LOG.debug("Schema dialog cancelled")
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