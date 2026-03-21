@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

class ConnectionSchemaDialog(
    private val project: Project,
    private val service: DriftLocatorProjectService,
    private val connectionId: String,
) : DialogWrapper(project) {
    private val schemaField = JBTextField()
    private val connectionNameLabel: String

    init {
        val connection = service.connections[connectionId]
        connectionNameLabel = connection?.name ?: connectionId
        schemaField.text = connection?.schema ?: DriftLocatorProjectService.DEFAULT_SCHEMA_NAME
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

class AddConnectionDialog(private val project: Project) : DialogWrapper(project) {
    private val nameField = JTextField()
    private val hostField = JTextField()
    private val portField = JTextField()
    private val databaseField = JTextField()
    private val usernameField = JTextField()
    private val passwordField = JPasswordField()
    private val schemaField = JTextField(DriftLocatorProjectService.DEFAULT_SCHEMA_NAME)

    init {
        init()
        title = "Add Database Connection"
    }

    override fun createCenterPanel(): JComponent =
        panel {
            row("Connection Name:") {
                cell(nameField)
                    .columns(15)
            }
            row("Host:") {
                cell(hostField)
                    .columns(15)
            }
            row("Port:") {
                cell(portField)
                    .columns(15)
            }
            row("Database:") {
                cell(databaseField)
                    .columns(15)
            }
            row("Schema:") {
                cell(schemaField)
                    .columns(15)
                    .comment("Database schema to compare (default: 'public')")
            }
            row("Username:") {
                cell(usernameField)
                    .columns(15)
            }
            row("Password:") {
                cell(passwordField)
                    .columns(15)
            }
        }

    fun getConnectionName(): String = nameField.text

    fun getHost(): String = hostField.text

    fun getPort(): Int = portField.text.toIntOrNull() ?: DEFAULT_POSTGRES_PORT

    fun getDatabase(): String = databaseField.text

    fun getSchema(): String = schemaField.text.ifEmpty { DriftLocatorProjectService.DEFAULT_SCHEMA_NAME }

    fun getUsername(): String = usernameField.text

    fun getPassword(): String? {
        val password = String(passwordField.password)
        return password.ifEmpty { null }
    }

    companion object {
        private const val DEFAULT_POSTGRES_PORT = 5432
    }
}

class ComparisonResultDialog(private val project: Project, private val resultText: String) : DialogWrapper(project) {
    init {
        init()
        title = "Schema Comparison Result"
    }

    override fun createCenterPanel(): JComponent {
        val textArea = JBTextArea(resultText)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.rows = 30
        textArea.columns = 80

        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(800, 600)

        return scrollPane
    }

    override fun createActions() = arrayOf(okAction)
}
