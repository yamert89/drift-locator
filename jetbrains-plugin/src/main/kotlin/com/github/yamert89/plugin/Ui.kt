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

class SchemaComparisonDialog(private val project: Project, private val service: DriftLocatorProjectService) : DialogWrapper(project) {
    private val connectionComboBox = ComboBox<String>()
    private val schema1Field = JBTextField()
    private val schema2Field = JBTextField()

    init {
        updateConnectionList()
        init()
        title = "Compare Database Schemas"
    }

    private fun updateConnectionList() {
        connectionComboBox.removeAllItems()
        service.connections.keys.forEach { connectionComboBox.addItem(it) }
    }

    override fun createCenterPanel(): JComponent =
        panel {
            row("Connection:") {
                cell(connectionComboBox)
            }
            row("Source Schema:") {
                cell(schema1Field)
            }
            row("Target Schema:") {
                cell(schema2Field)
            }
        }

    override fun doOKAction() {
        if (connectionComboBox.selectedItem == null) {
            Messages.showErrorDialog("Please select a connection", "Error")
            return
        }
        if (schema1Field.text.isEmpty() || schema2Field.text.isEmpty()) {
            Messages.showErrorDialog("Please enter both schema names", "Error")
            return
        }
        if (schema1Field.text == schema2Field.text) {
            Messages.showErrorDialog("Source and target schemas must be different", "Error")
            return
        }
        super.doOKAction()
    }

    fun getSelectedConnection(): String? = connectionComboBox.selectedItem as String?

    fun getSchema1(): String = schema1Field.text

    fun getSchema2(): String = schema2Field.text

    fun setSelectedConnection(connection: String) {
        if (connectionComboBox.itemCount == 0) {
            updateConnectionList()
        }
        connectionComboBox.selectedItem = connection
    }

    fun setSchema1(schema: String) {
        schema1Field.text = schema
    }
}

class AddConnectionDialog(private val project: Project) : DialogWrapper(project) {
    private val nameField = JTextField()
    private val hostField = JTextField()
    private val portField = JTextField()
    private val databaseField = JTextField()
    private val usernameField = JTextField()
    private val passwordField = JPasswordField()

    init {
        init()
        title = "Add Database Connection"
    }

    override fun createCenterPanel(): JComponent =
        panel {
            row("Connection Name:") {
                cell(nameField)
            }
            row("Host:") {
                cell(hostField)
            }
            row("Port:") {
                cell(portField)
            }
            row("Database:") {
                cell(databaseField)
            }
            row("Username:") {
                cell(usernameField)
            }
            row("Password (optional):") {
                cell(passwordField)
            }
        }

    fun getConnectionName(): String = nameField.text

    fun getHost(): String = hostField.text

    fun getPort(): Int = portField.text.toIntOrNull() ?: DEFAULT_POSTGRES_PORT

    fun getDatabase(): String = databaseField.text

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
