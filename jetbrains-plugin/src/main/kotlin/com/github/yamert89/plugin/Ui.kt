package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField

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

    private fun performComparison(
        project: Project,
        service: DriftLocatorProjectService,
        dialog: SchemaComparisonDialog,
    ) {
        // TODO: Implement actual comparison using core module
        Messages.showMessageDialog(
            project,
            "Schema comparison would be performed here",
            "Comparison Result",
            Messages.getInformationIcon(),
        )
    }
}

class SchemaComparisonDialog(private val project: Project, private val service: DriftLocatorProjectService) : DialogWrapper(project) {
    private val connectionComboBox = JComboBox<String>()
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

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc =
            java.awt.GridBagConstraints().apply {
                fill = java.awt.GridBagConstraints.HORIZONTAL
                insets.set(5, 5, 5, 5)
            }

        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Connection:"), gbc)
        gbc.gridx = 1
        panel.add(connectionComboBox, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JLabel("Schema 1:"), gbc)
        gbc.gridx = 1
        panel.add(schema1Field, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JLabel("Schema 2:"), gbc)
        gbc.gridx = 1
        panel.add(schema2Field, gbc)

        return panel
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

    fun setSchema2(schema: String) {
        schema2Field.text = schema
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
                    dialog.getConnectionName(),
                    dialog.getConnectionName(),
                    dialog.getUrl(),
                    dialog.getUsername(),
                    dialog.getPassword(),
                )
        }
    }
}

class AddConnectionDialog(private val project: Project) : DialogWrapper(project) {
    private val nameField = JBTextField()
    private val urlField = JBTextField()
    private val usernameField = JBTextField()
    private val passwordField = JPasswordField()

    init {
        init()
        title = "Add Database Connection"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc =
            java.awt.GridBagConstraints().apply {
                fill = java.awt.GridBagConstraints.HORIZONTAL
                insets.set(5, 5, 5, 5)
            }

        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Connection Name:"), gbc)
        gbc.gridx = 1
        panel.add(nameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JLabel("JDBC URL:"), gbc)
        gbc.gridx = 1
        panel.add(urlField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JLabel("Username:"), gbc)
        gbc.gridx = 1
        panel.add(usernameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 3
        panel.add(JLabel("Password:"), gbc)
        gbc.gridx = 1
        panel.add(passwordField, gbc)

        return panel
    }

    fun getConnectionName(): String = nameField.text

    fun getUrl(): String = urlField.text

    fun getUsername(): String = usernameField.text

    fun getPassword(): String = String(passwordField.password)
}
