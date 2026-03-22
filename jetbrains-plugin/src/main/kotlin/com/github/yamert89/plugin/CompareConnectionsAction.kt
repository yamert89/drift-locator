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
import com.intellij.openapi.wm.ToolWindowManager
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class CompareConnectionsAction : AnAction() {
    private val log = Logger.getInstance("CompareConnectionsAction")

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            return
        }

        val service = DriftLocatorProjectService.getInstance(project)
        val toolWindowPanel = getToolWindowPanel(project)

        log.info("CompareConnectionsAction triggered, connections count: ${service.connections.size}")

        // Check if there are enough connections
        if (service.connections.size < 2) {
            log.warn("Not enough connections configured")
            Messages.showErrorDialog(
                project,
                "At least 2 database connections are required for comparison. Please add more connections.",
                "Not Enough Connections",
            )
        } else {
            // Get selected connections from the tool window panel
            val selectedConnections = toolWindowPanel?.getSelectedConnections() ?: emptyList()

            if (selectedConnections.size != 2) {
                log.warn("Invalid number of connections selected: ${selectedConnections.size}")
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
            log.error("Source connection not found: $sourceConnectionId")
            Messages.showErrorDialog(project, "Source connection not found: $sourceConnectionId", "Error")
            return
        }

        if (targetConnection == null) {
            log.error("Target connection not found: $targetConnectionId")
            Messages.showErrorDialog(project, "Target connection not found: $targetConnectionId", "Error")
            return
        }

        log.info(
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
        sourceConnection: DatabaseConnection,
        targetConnection: DatabaseConnection,
    ) {
        try {
            val sourceSchema = fetchSchemaFromConnection(sourceConnection)
            log.debug("Source schema fetched, objects count: ${sourceSchema.objects.size}")

            val targetSchema = fetchSchemaFromConnection(targetConnection)
            log.debug("Target schema fetched, objects count: ${targetSchema.objects.size}")

            val diff = PostgresSchemaComparator().compare(sourceSchema, targetSchema)
            log.info(
                "Comparison complete - added: ${diff.added.size}, " +
                    "removed: ${diff.removed.size}, modified: ${diff.modified.size}",
            )

            showComparisonResultOnUiThread(project, sourceSchema, targetSchema, sourceConnection, targetConnection)
        } catch (e: Exception) {
            log.error("Error comparing schemas", e)
            ApplicationManager.getApplication().invokeLater {
                Messages.showErrorDialog(
                    project,
                    "Error comparing schemas: ${e.message}",
                    "Comparison Error",
                )
            }
        }
    }

    private fun fetchSchemaFromConnection(connection: DatabaseConnection) =
        PostgresConnectionManager
            .getConnection(
                host = connection.host,
                port = connection.port,
                database = connection.database,
                username = connection.username,
                password = connection.password,
            ).use { conn ->
                log.debug("Fetching schema: ${connection.schema} from ${connection.name}")
                PostgresSchemaComparator.Companion.fetchSchema(conn, connection.schema)
            }

    private fun showComparisonResultOnUiThread(
        project: Project,
        sourceSchema: DatabaseSchema,
        targetSchema: DatabaseSchema,
        sourceConnection: DatabaseConnection,
        targetConnection: DatabaseConnection,
    ) {
        ApplicationManager.getApplication().invokeLater {
            log.info("Opening diff viewer for schema comparison")
            showDiffViewer(project, sourceSchema, targetSchema, sourceConnection, targetConnection)
        }
    }

    private fun showDiffViewer(
        project: Project,
        sourceSchema: DatabaseSchema,
        targetSchema: DatabaseSchema,
        sourceConnection: DatabaseConnection,
        targetConnection: DatabaseConnection,
    ) {
        // Create report directory in project: .driftLocator/YYYY_MM_DD_HH_MM/
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")
        val timestamp = LocalDateTime.now().format(formatter)
        val baseDir = File(project.basePath, ".driftLocator")
        val reportDir = File(baseDir, timestamp)
        reportDir.mkdirs()

        val sourceFileName = "${sourceConnection.name}.txt"
        val targetFileName = "${targetConnection.name}.txt"

        val sourceFile = File(reportDir, sourceFileName)
        val targetFile = File(reportDir, targetFileName)

        // Write schema content to files (with schema name in header)
        sourceFile.writeText(DiffExporter.toText(sourceSchema, sourceConnection.schema))
        targetFile.writeText(DiffExporter.toText(targetSchema, targetConnection.schema))

        // Refresh virtual files
        val sourceVFile = VfsUtil.findFileByIoFile(sourceFile, true) ?: VfsUtil.findFileByIoFile(sourceFile, false)
        val targetVFile = VfsUtil.findFileByIoFile(targetFile, true) ?: VfsUtil.findFileByIoFile(targetFile, false)

        if (sourceVFile == null || targetVFile == null) {
            log.error("Failed to create virtual files for diff viewer")
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
            ToolWindowManager
                .getInstance(project)
                .getToolWindow("DriftLocator")
        return toolWindow
            ?.contentManager
            ?.getContent(0)
            ?.component as? DriftLocatorToolWindowPanel
    }
}