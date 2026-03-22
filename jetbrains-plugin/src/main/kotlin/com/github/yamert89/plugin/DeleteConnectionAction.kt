package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages


class DeleteConnectionAction : AnAction() {
    private val log = Logger.getInstance("CompareConnectionsAction")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)

        log.info("DeleteConnectionAction triggered, connections count: ${service.connections.size}")

        // Show dialog to select connection to delete
        val connections = service.connections.keys.toList()
        if (connections.isEmpty()) {
            log.warn("No connections to delete")
            Messages.showInfoMessage(project, "No connections to delete", "Delete Connection")
            return
        }

        val selectedConnection =
            Messages.showEditableChooseDialog(
                "Select connection to delete:",
                "Delete Connection",
                Messages.getQuestionIcon(),
                connections.toTypedArray(),
                connections.first(),
                null,
            )

        if (selectedConnection != null) {
            log.info("User selected connection to delete: '$selectedConnection'")
            val result =
                Messages.showYesNoDialog(
                    project,
                    "Are you sure you want to delete connection '$selectedConnection'?",
                    "Confirm Delete",
                    Messages.getQuestionIcon(),
                )
            if (result == Messages.YES) {
                log.info("Deleting connection '$selectedConnection'")
                service.removeConnection(selectedConnection)
                Messages.showInfoMessage(project, "Connection '$selectedConnection' deleted", "Delete Connection")
            } else {
                log.debug("Delete cancelled by user")
            }
        } else {
            log.debug("No connection selected for deletion")
        }
    }
}
