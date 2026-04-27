package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DatabaseConnection
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComponent

class EditConnectionDialog(project: Project, connection: DatabaseConnection) : DialogWrapper(project) {
    private val originalId = connection.id
    private val form = ConnectionDialogForm(ConnectionFormData.fromConnection(connection))

    init {
        init()
        title = "Edit Database Connection"
    }

    override fun createCenterPanel(): JComponent = form.createPanel()

    fun getConnectionData(): ConnectionFormData = form.getData()

}
