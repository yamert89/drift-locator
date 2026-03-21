package com.github.yamert89.plugin

import com.github.yamert89.core.DatabaseSchema
import com.github.yamert89.core.DiffExporter
import com.github.yamert89.postgresql.PostgresConnectionManager
import com.github.yamert89.postgresql.PostgresSchemaComparator
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

private val LOG = Logger.getInstance("DriftLocator.Actions")

class EditConnectionSchemaAction : AnAction() {
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
            com.intellij.openapi.wm.ToolWindowManager
                .getInstance(project)
                .getToolWindow("DriftLocator")
        return toolWindow
            ?.contentManager
            ?.getContent(0)
            ?.component as? DriftLocatorToolWindowPanel
    }
}

class CompareConnectionsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            return
        }

        val service = DriftLocatorProjectService.getInstance(project)
        val toolWindowPanel = getToolWindowPanel(project)

        LOG.info("CompareConnectionsAction triggered, connections count: ${service.connections.size}")

        // Check if there are enough connections
        if (service.connections.size < 2) {
            LOG.warn("Not enough connections configured")
            Messages.showErrorDialog(
                project,
                "At least 2 database connections are required for comparison. Please add more connections.",
                "Not Enough Connections",
            )
        } else {
            // Get selected connections from the tool window panel
            val selectedConnections = toolWindowPanel?.getSelectedConnections() ?: emptyList()

            if (selectedConnections.size != 2) {
                LOG.warn("Invalid number of connections selected: ${selectedConnections.size}")
                Messages.showErrorDialog(
                    project,
                    "Please select exactly 2 connections in the list to compare.",
                    "Selection Error",
                )
            } else {
                val sourceConnectionId = selectedConnections[0]
                val targetConnectionId = selectedConnections[1]
                performComparison(project, service, sourceConnectionId, targetConnectionId)
            }
        }
    }

    private fun performComparison(
        project: Project,
        service: DriftLocatorProjectService,
        sourceConnectionId: String,
        targetConnectionId: String,
    ) {
        val sourceConnection = service.connections[sourceConnectionId]
        val targetConnection = service.connections[targetConnectionId]

        if (sourceConnection == null) {
            LOG.error("Source connection not found: $sourceConnectionId")
            Messages.showErrorDialog(project, "Source connection not found: $sourceConnectionId", "Error")
            return
        }

        if (targetConnection == null) {
            LOG.error("Target connection not found: $targetConnectionId")
            Messages.showErrorDialog(project, "Target connection not found: $targetConnectionId", "Error")
            return
        }

        LOG.info(
            "Starting schema comparison between connections: '$sourceConnectionId' " +
                "(schema: '${sourceConnection.schema}') and '$targetConnectionId' " +
                "(schema: '${targetConnection.schema}')",
        )

        ApplicationManager.getApplication().executeOnPooledThread {
            executeComparison(project, sourceConnection, targetConnection)
        }
    }

    private fun executeComparison(
        project: Project,
        sourceConnection: DriftLocatorProjectService.DatabaseConnection,
        targetConnection: DriftLocatorProjectService.DatabaseConnection,
    ) {
        try {
            val sourceSchema = fetchSchemaFromConnection(sourceConnection)
            LOG.debug("Source schema fetched, objects count: ${sourceSchema.objects.size}")

            val targetSchema = fetchSchemaFromConnection(targetConnection)
            LOG.debug("Target schema fetched, objects count: ${targetSchema.objects.size}")

            val diff = PostgresSchemaComparator().compare(sourceSchema, targetSchema)
            LOG.info(
                "Comparison complete - added: ${diff.added.size}, " +
                    "removed: ${diff.removed.size}, modified: ${diff.modified.size}",
            )

            showComparisonResultOnUiThread(project, sourceSchema, targetSchema, sourceConnection, targetConnection)
        } catch (e: Exception) {
            LOG.error("Error comparing schemas", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "Error comparing schemas: ${e.message}",
                    "Comparison Error",
                )
            }
        }
    }

    private fun fetchSchemaFromConnection(connection: DriftLocatorProjectService.DatabaseConnection) =
        PostgresConnectionManager
            .getConnection(
                host = connection.host,
                port = connection.port,
                database = connection.database,
                username = connection.username,
                password = connection.password,
            ).use { conn ->
                LOG.debug("Fetching schema: ${connection.schema} from ${connection.name}")
                PostgresSchemaComparator.fetchSchema(conn, connection.schema)
            }

    private fun showComparisonResultOnUiThread(
        project: Project,
        sourceSchema: DatabaseSchema,
        targetSchema: DatabaseSchema,
        sourceConnection: DriftLocatorProjectService.DatabaseConnection,
        targetConnection: DriftLocatorProjectService.DatabaseConnection,
    ) {
        ApplicationManager.getApplication().invokeLater {
            LOG.info("Opening diff viewer for schema comparison")
            showDiffViewer(project, sourceSchema, targetSchema, sourceConnection, targetConnection)
        }
    }

    private fun showDiffViewer(
        project: Project,
        sourceSchema: DatabaseSchema,
        targetSchema: DatabaseSchema,
        sourceConnection: DriftLocatorProjectService.DatabaseConnection,
        targetConnection: DriftLocatorProjectService.DatabaseConnection,
    ) {
        // Create temporary files with schema content
        val tempDir = File(System.getProperty("java.io.tmpdir"), "drift-locator")
        tempDir.mkdirs()

        val sourceFileName = "${sourceConnection.name}_${sourceConnection.schema}.txt"
        val targetFileName = "${targetConnection.name}_${targetConnection.schema}.txt"

        val sourceFile = File(tempDir, sourceFileName)
        val targetFile = File(tempDir, targetFileName)

        // Write schema content to files
        sourceFile.writeText(DiffExporter.toText(sourceSchema))
        targetFile.writeText(DiffExporter.toText(targetSchema))

        // Refresh virtual files
        val sourceVFile = VfsUtil.findFileByIoFile(sourceFile, true) ?: VfsUtil.findFileByIoFile(sourceFile, false)
        val targetVFile = VfsUtil.findFileByIoFile(targetFile, true) ?: VfsUtil.findFileByIoFile(targetFile, false)

        if (sourceVFile == null || targetVFile == null) {
            LOG.error("Failed to create virtual files for diff viewer")
            Messages.showErrorDialog(project, "Failed to create temporary files for comparison", "Error")
            return
        }

        // Refresh to ensure content is loaded
        sourceVFile.refresh(false, false)
        targetVFile.refresh(false, false)

        // Open files in editor first (so they appear in recent files and can be compared)
        FileEditorManager.getInstance(project).openFile(sourceVFile, false)
        FileEditorManager.getInstance(project).openFile(targetVFile, false)

        // Create diff content using the file content
        val contentFactory = DiffContentFactory.getInstance()
        val sourceContent = contentFactory.create(project, sourceVFile)
        val targetContent = contentFactory.create(project, targetVFile)

        // Create and show diff request
        val diffRequest =
            SimpleDiffRequest(
                "Schema Comparison: ${sourceConnection.name} vs ${targetConnection.name}",
                sourceContent,
                targetContent,
                "${sourceConnection.name} (${sourceConnection.schema})",
                "${targetConnection.name} (${targetConnection.schema})",
            )

        DiffManager.getInstance().showDiff(project, diffRequest)
    }

    private fun getToolWindowPanel(project: Project): DriftLocatorToolWindowPanel? {
        val toolWindow =
            com.intellij.openapi.wm.ToolWindowManager
                .getInstance(project)
                .getToolWindow("DriftLocator")
        return toolWindow
            ?.contentManager
            ?.getContent(0)
            ?.component as? DriftLocatorToolWindowPanel
    }
}

class AddConnectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LOG.info("AddConnectionAction triggered")

        val dialog = AddConnectionDialog(project)
        if (dialog.showAndGet()) {
            val service = DriftLocatorProjectService.getInstance(project)
            val connection =
                DriftLocatorProjectService.DatabaseConnection(
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
            LOG.info(
                "Adding connection '${connection.name}' " +
                    "(${connection.host}:${connection.port}/${connection.database}, " +
                    "schema=${connection.schema}, username=${connection.username}, password=$passwordStatus)",
            )

            service.addConnection(connection)
            // Validate connection in background
            validateConnectionInBackground(project, connection, service)
        } else {
            LOG.debug("Add connection dialog cancelled")
        }
    }
}

class DeleteConnectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)

        LOG.info("DeleteConnectionAction triggered, connections count: ${service.connections.size}")

        // Show dialog to select connection to delete
        val connections = service.connections.keys.toList()
        if (connections.isEmpty()) {
            LOG.warn("No connections to delete")
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
            LOG.info("User selected connection to delete: '$selectedConnection'")
            val result =
                Messages.showYesNoDialog(
                    project,
                    "Are you sure you want to delete connection '$selectedConnection'?",
                    "Confirm Delete",
                    Messages.getQuestionIcon(),
                )
            if (result == Messages.YES) {
                LOG.info("Deleting connection '$selectedConnection'")
                service.removeConnection(selectedConnection)
                Messages.showInfoMessage(project, "Connection '$selectedConnection' deleted", "Delete Connection")
            } else {
                LOG.debug("Delete cancelled by user")
            }
        } else {
            LOG.debug("No connection selected for deletion")
        }
    }
}
