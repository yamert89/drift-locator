@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class DriftLocatorToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DriftLocatorToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class DriftLocatorToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val service = DriftLocatorProjectService.getInstance(project)
    private val connectionList = JBList<String>()
    private val listModel = DefaultListModel<String>()

    companion object {
        private const val BUTTON_SPACING = 5
    }

    init {
        initUI()
        updateConnectionList()
    }

    private fun initUI() {
        // Create buttons using helper methods
        val addButton = createAddButton()
        val deleteButton = createDeleteButton()
        val compareButton = createCompareButton()

        // Toolbar panel
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        toolbar.add(addButton)
        toolbar.add(Box.createHorizontalStrut(BUTTON_SPACING))
        toolbar.add(deleteButton)
        toolbar.add(Box.createHorizontalStrut(BUTTON_SPACING))
        toolbar.add(compareButton)
        toolbar.add(Box.createHorizontalGlue())

        // List of connections
        connectionList.model = listModel
        connectionList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        val scrollPane = JBScrollPane(connectionList)

        // Build UI with DSL
        val contentPanel =
            panel {
                row {
                    cell(toolbar)
                }
                row {
                    cell(scrollPane).apply {
                        resizableColumn()
                    }
                }.resizableRow()
            }

        // Add to main panel (which uses BorderLayout)
        add(contentPanel, BorderLayout.CENTER)
    }

    private fun updateConnectionList() {
        listModel.clear()
        service.connections.keys.forEach { listModel.addElement(it) }
    }

    private fun createAddButton(): JButton {
        val button = JButton("Add Connection")
        button.addActionListener {
            val dialog = AddConnectionDialog(project)
            if (dialog.showAndGet()) {
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
                updateConnectionList()
            }
        }
        return button
    }

    private fun createDeleteButton(): JButton {
        val button = JButton("Delete Connection")
        button.addActionListener {
            val selected = connectionList.selectedValuesList
            if (selected.isNotEmpty()) {
                selected.forEach { service.connections.remove(it) }
                updateConnectionList()
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Please select at least one connection to delete",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE,
                )
            }
        }
        return button
    }

    private fun createCompareButton(): JButton {
        val button = JButton("Compare")
        button.addActionListener {
            val selected = connectionList.selectedValuesList
            if (selected.isNotEmpty()) {
                val dialog = SchemaComparisonDialog(project, service)
                dialog.setSelectedConnection(selected[0])
                if (dialog.showAndGet()) {
                    // Perform comparison logic (to be implemented)
                }
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Please select at least one connection to compare",
                    "Selection Error",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }
        return button
    }
}
