package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DatabaseConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPasswordField

/**
 * Dialog for prompting passwords when comparing connections.
 * Shows password fields only for connections that don't have saved passwords.
 */
class PasswordPromptDialog(
    project: Project,
    private val sourceConnection: DatabaseConnection,
    private val targetConnection: DatabaseConnection,
) : DialogWrapper(project) {
    private val sourcePasswordField = JPasswordField()
    private val targetPasswordField = JPasswordField()

    init {
        init()
        title = "Enter Database Passwords"
    }

    override fun createCenterPanel(): JComponent =
        panel {
            val sourceNeedsPassword = sourceConnection.password.isNullOrBlank()
            val targetNeedsPassword = targetConnection.password.isNullOrBlank()

            if (sourceNeedsPassword) {
                row("${sourceConnection.name} password:") {
                    cell(sourcePasswordField)
                        .columns(20)
                }
            }

            if (targetNeedsPassword) {
                row("${targetConnection.name} password:") {
                    cell(targetPasswordField)
                        .columns(20)
                }
            }
        }

    fun getSourcePassword(): String? = sourcePasswordField.getPassword(sourceConnection)

    fun getTargetPassword(): String? = targetPasswordField.getPassword(targetConnection)

    private fun JPasswordField.getPassword(connection: DatabaseConnection): String? {
        if (!connection.savePassword) {
            val password = String(this.password)
            return password.ifEmpty { null }
        }
        // If savePassword is true but password was not loaded, use the field
        if (connection.password.isNullOrBlank()) {
            val password = String(this.password)
            return password.ifEmpty { null }
        }
        return connection.password
    }
}
