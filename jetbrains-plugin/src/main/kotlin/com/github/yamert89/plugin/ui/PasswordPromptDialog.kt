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
            val sourceNeedsPassword = sourceConnection.savePassword && sourceConnection.password.isNullOrBlank()
            val targetNeedsPassword = targetConnection.savePassword && targetConnection.password.isNullOrBlank()

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

            if (!sourceNeedsPassword && !targetNeedsPassword) {
                row {
                    label("No passwords required for these connections.")
                }
            }
        }

    fun getSourcePassword(): String? {
        if (!sourceConnection.savePassword) {
            val password = String(sourcePasswordField.password)
            return password.ifEmpty { null }
        }
        // If savePassword is true but password was not loaded, use the field
        if (sourceConnection.password.isNullOrBlank()) {
            val password = String(sourcePasswordField.password)
            return password.ifEmpty { null }
        }
        return sourceConnection.password
    }

    fun getTargetPassword(): String? {
        if (!targetConnection.savePassword) {
            val password = String(targetPasswordField.password)
            return password.ifEmpty { null }
        }
        // If savePassword is true but password was not loaded, use the field
        if (targetConnection.password.isNullOrBlank()) {
            val password = String(targetPasswordField.password)
            return password.ifEmpty { null }
        }
        return targetConnection.password
    }
}
