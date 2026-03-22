@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.github.yamert89.core.Defaults
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField
import javax.swing.text.JTextComponent

class ConnectionSchemaDialog(
    project: Project,
    private val service: DriftLocatorProjectService,
    connectionId: String,
) : DialogWrapper(project) {
    private val schemaField = JBTextField()
    private val connectionNameLabel: String

    init {
        val connection = service.connections[connectionId]
        connectionNameLabel = connection?.name ?: connectionId
        schemaField.text = connection?.schema
        init()
        title = "Schema for '$connectionNameLabel'"
    }

    override fun createCenterPanel(): JComponent =
        panel {
            row("Schema Name:") {
                cell(schemaField)
                    .comment("The schema name to use for this connection (e.g., 'public')")
            }
        }

    override fun doOKAction() {
        if (schemaField.text.isEmpty()) {
            Messages.showErrorDialog("Please enter a schema name", "Error")
            return
        }
        super.doOKAction()
    }

    fun getSchemaName(): String = schemaField.text.trim()
}

class AddConnectionDialog(project: Project, defaults: Defaults) : DialogWrapper(project) {
    private val nameField = JTextField()
    private val hostField = JTextField(defaults.host)
    private val portField = JTextField(defaults.port.toString())
    private val databaseField = JTextField(defaults.database)
    private val usernameField = JTextField(defaults.username)
    private val passwordField = JPasswordField()
    private val schemaField = JTextField(defaults.schema)

    init {
        init()
        title = "Add Database Connection"
    }

    companion object {
        const val REQUIRED = "Field must be not empty"
    }

    @Suppress("Unchecked_cast")
    private fun <T: JComponent> Cell<T>.required() {
        assert(this.component is JTextField)
        this as Cell<JTextField>
        this.validationOnApply { if (it.text.isEmpty()) ValidationInfo(REQUIRED) else null }
    }


    override fun createCenterPanel(): JComponent =
        panel {
            row("Connection Name:") {
                cell(nameField)
                    .columns(15)
                    .required()
            }
            row("Host:") {
                cell(hostField)
                    .columns(15)
                    .required()
            }
            row("Port:") {
                cell(portField)
                    .columns(15)
                    .validationOnApply {
                        runCatching { it.text.toInt() }
                            .fold(
                                onSuccess = { null },
                                onFailure = { ValidationInfo("Only integers allowed") }
                            )
                    }
            }
            row("Database:") {
                cell(databaseField)
                    .columns(15)
                    .required()
            }
            row("Schema:") {
                cell(schemaField)
                    .columns(15)
                    .required()
            }
            row("Username:") {
                cell(usernameField)
                    .columns(15)
                    .required()
            }
            row("Password:") {
                cell(passwordField)
                    .columns(15)
            }
        }

    fun getConnectionName(): String = nameField.text

    fun getHost(): String = hostField.text

    fun getPort(): Int = portField.text.toInt()

    fun getDatabase(): String = databaseField.text

    fun getSchema(): String = schemaField.text

    fun getUsername(): String = usernameField.text

    fun getPassword(): String? {
        val password = String(passwordField.password)
        return password.ifEmpty { null }
    }

}
