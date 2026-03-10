package com.github.yamert89.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
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

    init {
        initUI()
        updateConnectionList()
    }

    private fun initUI() {
        // Toolbar with buttons
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        val addButton = JButton("Add Connection")
        val deleteButton = JButton("Delete Connection")
        val compareButton = JButton("Compare")

        addButton.addActionListener {
            val dialog = AddConnectionDialog(project)
            if (dialog.showAndGet()) {
                service.connections[dialog.getConnectionName()] =
                    DriftLocatorProjectService.DatabaseConnection(
                        dialog.getConnectionName(),
                        dialog.getConnectionName(),
                        dialog.getUrl(),
                        dialog.getUsername(),
                        dialog.getPassword(),
                    )
                updateConnectionList()
            }
        }

        deleteButton.addActionListener {
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

        compareButton.addActionListener {
            val selected = connectionList.selectedValuesList
            if (selected.isNotEmpty()) {
                val dialog = SchemaComparisonDialog(project, service)
                dialog.setSelectedConnection(selected[0])
                if (dialog.showAndGet()) {
                    // Perform comparison
                    // TODO: implement comparison logic
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

        toolbar.add(addButton)
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(deleteButton)
        toolbar.add(Box.createHorizontalStrut(5))
        toolbar.add(compareButton)
        toolbar.add(Box.createHorizontalGlue())

        // List of connections
        connectionList.model = listModel
        connectionList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        val scrollPane = JBScrollPane(connectionList)

        // Add components
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun updateConnectionList() {
        listModel.clear()
        service.connections.keys.forEach { listModel.addElement(it) }
    }
}
