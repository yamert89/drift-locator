package com.github.yamert89.plugin

import com.github.yamert89.core.DiffExporter
import com.github.yamert89.postgresql.PostgresConnectionManager
import com.github.yamert89.postgresql.PostgresSchemaComparator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

private val LOG = Logger.getInstance("DriftLocator.Actions")

class DriftLocatorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)

        LOG.info("DriftLocatorAction triggered, connections count: ${service.connections.size}")

        // Check if there are connections
        if (service.connections.isEmpty()) {
            LOG.warn("No connections configured")
            Messages.showErrorDialog(
                project,
                "No database connections configured. Please add a connection first.",
                "No Connections",
            )
            return
        }

        val dialog = SchemaComparisonDialog(project, service)
        if (dialog.showAndGet()) {
            LOG.info(
                "Schema comparison dialog confirmed, connection: ${dialog.getSelectedConnection()}, " +
                    "schemas: ${dialog.getSchema1()} vs ${dialog.getSchema2()}",
            )
            performComparison(project, service, dialog)
        } else {
            LOG.debug("Schema comparison dialog cancelled")
        }
    }

    private fun performComparison(
        project: Project,
        service: DriftLocatorProjectService,
        dialog: SchemaComparisonDialog,
    ) {
        val connectionId = dialog.getSelectedConnection() ?: return
        val sourceSchemaName = dialog.getSchema1()
        val targetSchemaName = dialog.getSchema2()

        val connection =
            service.connections[connectionId]
                ?: run {
                    LOG.error("Connection not found: $connectionId")
                    Messages.showErrorDialog(project, "Connection not found: $connectionId", "Error")
                    return
                }

        LOG.info(
            "Starting schema comparison using connection '${connection.name}' " +
                "for schemas '$sourceSchemaName' vs '$targetSchemaName'",
        )

        // Perform comparison in background
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                LOG.debug("Opening database connection via PostgresConnectionManager")
                PostgresConnectionManager
                    .getConnection(
                        host = connection.host,
                        port = connection.port,
                        database = connection.database,
                        username = connection.username,
                        password = connection.password,
                    ).use { conn ->
                        LOG.debug("Fetching source schema: $sourceSchemaName")
                        val sourceSchema = PostgresSchemaComparator.fetchSchema(conn, sourceSchemaName)
                        LOG.debug("Source schema fetched, objects count: ${sourceSchema.objects.size}")

                        LOG.debug("Fetching target schema: $targetSchemaName")
                        val targetSchema = PostgresSchemaComparator.fetchSchema(conn, targetSchemaName)
                        LOG.debug("Target schema fetched, objects count: ${targetSchema.objects.size}")

                        LOG.debug("Comparing schemas")
                        val comparator = PostgresSchemaComparator()
                        val diff = comparator.compare(sourceSchema, targetSchema)
                        LOG.info(
                            "Comparison complete - added: ${diff.added.size}, " +
                                "removed: ${diff.removed.size}, modified: ${diff.modified.size}",
                        )

                        // Export to text
                        val resultText = DiffExporter.toText(diff)

                        // Show result on UI thread
                        ApplicationManager.getApplication().invokeLater {
                            if (diff.added.isEmpty() && diff.removed.isEmpty() && diff.modified.isEmpty()) {
                                LOG.info("Schemas are identical")
                                Messages.showInfoMessage(
                                    project,
                                    "Schemas are identical. No differences found.",
                                    "Comparison Result",
                                )
                            } else {
                                LOG.info("Showing comparison result dialog")
                                showComparisonResult(project, resultText)
                            }
                        }
                    }
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
    }

    private fun showComparisonResult(project: Project, resultText: String) {
        val dialog = ComparisonResultDialog(project, resultText)
        dialog.show()
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
                )
            val passwordStatus = if (connection.password.isNullOrEmpty()) "not set" else "set"
            LOG.info(
                "Adding connection '${connection.name}' " +
                    "(${connection.host}:${connection.port}/${connection.database}, " +
                    "username=${connection.username}, password=$passwordStatus)",
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
