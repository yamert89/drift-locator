package com.github.yamert89.plugin

import com.github.yamert89.plugin.ui.notifyInfo
import com.github.yamert89.postgresql.PostgresConnectionTester
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.sql.SQLException

private val LOG = Logger.getInstance("DriftLocator.ConnectionValidation")

/**
 * Validates a database connection in a background thread and invokes callbacks accordingly.
 * Updates the connection's validation status in the service based on the result.
 *
 * @param project The project context (used for UI operations if needed).
 * @param connection The connection to validate.
 * @param onSuccess Optional callback invoked on the UI thread when the connection is successful.
 * @param onFailure Optional callback invoked on the UI thread when the connection fails.
 *                  If not provided, a default error dialog will be shown.
 * @param service The service for updating connection validation status.
 */
fun validateConnectionInBackground(
    project: Project,
    connection: DatabaseConnection,
    service: DriftLocatorProjectService,
    onSuccess: (() -> Unit)? = null,
    onFailure: (() -> Unit)? = null,
) {
    LOG.info(
        "Starting connection validation for '${connection.name}' " +
            "(${connection.host}:${connection.port}/${connection.database})",
    )

    ApplicationManager.getApplication().executeOnPooledThread {
        val isConnected =
            try {
                PostgresConnectionTester.testConnection(
                    host = connection.host,
                    port = connection.port,
                    database = connection.database,
                    username = connection.username,
                    password = connection.password,
                )
            } catch (e: SQLException) {
                LOG.error("Connection validation threw exception for '${connection.name}'", e)
                false
            }
        ApplicationManager.getApplication().invokeLater {
            if (isConnected) {
                val msg = "Connection '${connection.name}' validated successfully"
                LOG.info(msg)
                project.notifyInfo(msg)
                // Reset connection status to valid
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.updateConnectionValidationStatus(connection.id, true)
                }
                onSuccess?.invoke()
            } else {
                LOG.warn("Connection '${connection.name}' validation failed")
                // Update connection status to invalid
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.updateConnectionValidationStatus(connection.id, false)
                }
                if (onFailure != null) {
                    onFailure.invoke()
                } else {
                    Messages.showErrorDialog(
                        project,
                        "Connection '${connection.name}' failed to connect. " +
                            "Please check the connection details.",
                        "Connection Error",
                    )
                }
            }
        }
    }
}
