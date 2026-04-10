@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DriftLocatorProjectService
import com.intellij.icons.AllIcons
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
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Data class representing an item in the connection list.
 * Stores both connection ID and validation status for displaying icons.
 */
data class ConnectionListItem(val id: String, val isValid: Boolean)

class DriftLocatorToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DriftLocatorToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class DriftLocatorToolWindowPanel(project: Project) : JPanel(BorderLayout()) {
    private val service = DriftLocatorProjectService.getInstance(project)
    private val connectionList = JBList<ConnectionListItem>()
    private val listModel = DefaultListModel<ConnectionListItem>()

    init {
        createToolbarWithActions()
        updateConnectionList()
        // Subscribe to connection changes
        service.addConnectionChangeListener { updateConnectionList() }
    }

    private fun createToolbarWithActions() {
        val actManager = ActionManager.getInstance()
        val group = actManager.getAction("com.github.yamert89.plugin.toolWindow") as ActionGroup
        val toolBar = actManager.createActionToolbar(ActionPlaces.TOOLBAR, group, true)
        toolBar.targetComponent = this
        add(toolBar.component, BorderLayout.PAGE_START)
        connectionList.model = listModel
        connectionList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        connectionList.cellRenderer = ConnectionListCellRenderer()
        val scrollPane = JBScrollPane(connectionList)
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun updateConnectionList() {
        listModel.clear()
        service.connections.values
            .sortedBy { it.name }
            .forEach { conn ->
                listModel.addElement(ConnectionListItem(conn.id, conn.isValid))
            }
    }

    /**
     * Returns the list of currently selected connection IDs.
     */
    fun getSelectedConnections(): List<String> = connectionList.selectedValuesList.map { it.id }
}

/**
 * Custom cell renderer for connection list items.
 * Displays a green icon for valid connections and a red icon for invalid ones.
 */
class ConnectionListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val label =
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                as JLabel

        if (value is ConnectionListItem) {
            label.text = value.id
            label.icon =
                if (value.isValid) {
                    AllIcons.Actions.Commit // Green checkmark icon
                } else {
                    AllIcons.Actions.Cancel // Red X icon
                }
        }

        return label
    }
}
