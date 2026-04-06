package com.github.yamert89.plugin

import com.github.yamert89.plugin.ui.DriftLocatorToolWindowPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager

class DeleteConnectionAction : AnAction() {
    private val log = Logger.getInstance("DeleteConnectionAction")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)
        val toolWindowPanel = getToolWindowPanel(project)

        log.info("DeleteConnectionAction triggered, connections count: ${service.connections.size}")

        // Check if there are connections
        if (service.connections.isEmpty()) {
            log.warn("No connections to delete")
            Messages.showInfoMessage(project, "No connections to delete", "Delete Connection")
            return
        }

        // Get selected connections from the tool window panel
        val selectedConnections = toolWindowPanel?.getSelectedConnections() ?: emptyList()

        when {
            selectedConnections.isEmpty() -> {
                log.warn("No connection selected in the list")
                Messages.showWarningDialog(
                    project,
                    "Please select a connection in the list to delete.",
                    "No Selection",
                )
            }
            selectedConnections.size > 1 -> {
                log.warn("Multiple connections selected: ${selectedConnections.size}")
                Messages.showWarningDialog(
                    project,
                    "Please select only one connection to delete.",
                    "Multiple Selection",
                )
            }
            else -> {
                val connectionToDelete = selectedConnections.first()
                log.info("Selected connection to delete: '$connectionToDelete'")

                // Confirm deletion
                val result =
                    Messages.showYesNoDialog(
                        project,
                        "Are you sure you want to delete connection '$connectionToDelete'?",
                        "Confirm Delete",
                        Messages.getQuestionIcon(),
                    )

                if (result == Messages.YES) {
                    log.info("Deleting connection '$connectionToDelete'")
                    service.removeConnection(connectionToDelete)
                } else {
                    log.debug("Delete cancelled by user")
                }
            }
        }
    }

    private fun getToolWindowPanel(project: Project): DriftLocatorToolWindowPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Drift Locator")
        return toolWindow
            ?.contentManager
            ?.getContent(0)
            ?.component as? DriftLocatorToolWindowPanel
    }
}
