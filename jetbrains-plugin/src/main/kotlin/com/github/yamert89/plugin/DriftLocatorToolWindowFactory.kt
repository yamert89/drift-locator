@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.DefaultListModel
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
        // Subscribe to connection changes
        service.addConnectionChangeListener { updateConnectionList() }
    }

    private fun initUI() {
        // Create toolbar with actions
        val actManager = ActionManager.getInstance()
        val group = actManager.getAction("com.github.yamert89.plugin.toolWindow") as ActionGroup
        val toolBar = actManager.createActionToolbar(ActionPlaces.TOOLBAR, group, true)
        toolBar.targetComponent = this
        add(toolBar.component, BorderLayout.PAGE_START)

        // List of connections
        connectionList.model = listModel
        connectionList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        val scrollPane = JBScrollPane(connectionList)

        // Add to main panel
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun updateConnectionList() {
        listModel.clear()
        service.connections.keys.forEach { listModel.addElement(it) }
    }

    /**
     * Returns the list of currently selected connection names.
     */
    fun getSelectedConnections(): List<String> = connectionList.selectedValuesList
}
