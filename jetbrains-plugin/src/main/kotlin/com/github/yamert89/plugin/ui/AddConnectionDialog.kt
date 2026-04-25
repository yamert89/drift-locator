package com.github.yamert89.plugin.ui

import com.github.yamert89.core.Defaults
import com.github.yamert89.plugin.DatabaseConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class AddConnectionDialog(
    project: Project,
    defaults: Defaults,
    lastConnection: DatabaseConnection? = null,
) : DialogWrapper(project) {
    private val form = ConnectionDialogForm(ConnectionFormData.forNewConnection(defaults, lastConnection))

    init {
        init()
        title = "Add Database Connection"
    }

    override fun createCenterPanel(): JComponent = form.createPanel()

    fun getConnectionData(): ConnectionFormData = form.getData()
}
