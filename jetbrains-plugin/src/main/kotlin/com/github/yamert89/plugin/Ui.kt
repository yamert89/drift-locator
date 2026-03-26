@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.github.yamert89.core.Defaults
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

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

class AddConnectionDialog(
    project: Project,
    defaults: Defaults,
    lastConnection: DatabaseConnection? = null
) : DialogWrapper(project) {
    private val nameField = JTextField()
    private val hostField = JTextField(lastConnection?.host ?: defaults.host)
    private val portField = JTextField((lastConnection?.port ?: defaults.port).toString())
    private val databaseField = JTextField(lastConnection?.database ?: defaults.database)
    private val usernameField = JTextField(lastConnection?.username ?: defaults.username)
    private val passwordField = JPasswordField()
    private val schemaField = JTextField(lastConnection?.schema ?: defaults.schema)

    init {
        init()
        title = "Add Database Connection"
    }

    companion object {
        const val REQUIRED = "Field must be not empty"
        const val COLUMN_SIZE = 15
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
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Host:") {
                cell(hostField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Port:") {
                cell(portField)
                    .columns(COLUMN_SIZE)
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
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Schema:") {
                cell(schemaField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Username:") {
                cell(usernameField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Password:") {
                cell(passwordField)
                    .columns(COLUMN_SIZE)
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
