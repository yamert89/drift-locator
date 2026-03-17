package com.github.yamert89.plugin

import com.github.yamert89.postgresql.PostgresConnectionTester
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

private val LOG = Logger.getInstance("DriftLocator.ConnectionValidation")

/**
 * Validates a database connection in a background thread and invokes callbacks accordingly.
 *
 * @param project The project context (used for UI operations if needed).
 * @param connection The connection to validate.
 * @param onSuccess Optional callback invoked on the UI thread when the connection is successful.
 * @param onFailure Optional callback invoked on the UI thread when the connection fails.
 *                  If not provided, a default error dialog will be shown and the connection will be removed from the service.
 * @param service The service from which the connection can be removed (required for default failure handling).
 */
fun validateConnectionInBackground(
    project: Project,
    connection: DriftLocatorProjectService.DatabaseConnection,
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
            } catch (e: Exception) {
                LOG.error("Connection validation threw exception for '${connection.name}'", e)
                false
            }
        ApplicationManager.getApplication().invokeLater {
            if (isConnected) {
                LOG.info("Connection '${connection.name}' validated successfully")
                onSuccess?.invoke()
            } else {
                LOG.warn("Connection '${connection.name}' validation failed")
                if (onFailure != null) {
                    onFailure.invoke()
                } else {
                    // Default failure behavior: remove connection and show error dialog
                    LOG.info(
                        "Removing connection '${connection.name}' from service due to " +
                            "validation failure",
                    )
                    service.removeConnection(connection.id)
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
