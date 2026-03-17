@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.github.yamert89.postgresql.PostgresConnectionTester
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
        val actManager = ActionManager.getInstance()
        val group = actManager.getAction("com.github.yamert89.plugin.toolWindow") as ActionGroup
        val toolBar = actManager.createActionToolbar(ActionPlaces.TOOLBAR, group, true)
        toolBar.targetComponent = this
        add(toolBar.component, BorderLayout.PAGE_START)

        // List of connections
        connectionList.model = listModel
        connectionList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        val scrollPane = JBScrollPane(connectionList)

        // Add to main panel (which uses BorderLayout)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun updateConnectionList() {
        listModel.clear()
        service.connections.keys.forEach { listModel.addElement(it) }
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
