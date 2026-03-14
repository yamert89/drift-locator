@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JTextField

class DriftLocatorAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = DriftLocatorProjectService.getInstance(project)

        val dialog = SchemaComparisonDialog(project, service)
        if (dialog.showAndGet()) {
            // Perform comparison logic here
            performComparison(project, service, dialog)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun performComparison(
        project: Project,
        unusedService: DriftLocatorProjectService,
        unusedDialog: SchemaComparisonDialog,
    ) {
        // Implement actual comparison using core module
        Messages.showMessageDialog(
            project,
            "Schema comparison would be performed here",
            "Comparison Result",
            Messages.getInformationIcon(),
        )
    }
}

class SchemaComparisonDialog(private val project: Project, private val service: DriftLocatorProjectService) : DialogWrapper(project) {
    private val connectionComboBox = ComboBox<String>()
    private val schema1Field = JBTextField()

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
            row("Schema 1:") {
                cell(schema1Field)
            }
        }

    override fun doOKAction() {
        if (connectionComboBox.selectedItem == null) {
            Messages.showErrorDialog("Please select a connection", "Error")
            return
        }
        if (schema1Field.text.isEmpty()) {
            Messages.showErrorDialog("Please enter both schema names", "Error")
            return
        }
        super.doOKAction()
    }

    fun getSelectedConnection(): String? = connectionComboBox.selectedItem as String?

    fun getSchema1(): String = schema1Field.text

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

class AddConnectionAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = AddConnectionDialog(project)
        if (dialog.showAndGet()) {
            val service = DriftLocatorProjectService.getInstance(project)
            service.connections[dialog.getConnectionName()] =
                DriftLocatorProjectService.DatabaseConnection(
                    id = dialog.getConnectionName(),
                    name = dialog.getConnectionName(),
                    host = dialog.getHost(),
                    port = dialog.getPort(),
                    database = dialog.getDatabase(),
                    username = dialog.getUsername(),
                    password = dialog.getPassword(),
                )
        }
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
            row("Password:") {
                cell(passwordField)
            }
        }

    fun getConnectionName(): String = nameField.text

    fun getHost(): String = hostField.text

    fun getPort(): Int = portField.text.toIntOrNull() ?: DEFAULT_POSTGRES_PORT

    fun getDatabase(): String = databaseField.text

    fun getUsername(): String = usernameField.text

    fun getPassword(): String = String(passwordField.password)

    companion object {
        private const val DEFAULT_POSTGRES_PORT = 5432
    }
}
