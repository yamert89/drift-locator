package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DatabaseAdapters
import com.github.yamert89.plugin.DatabaseType
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

class ConnectionDialogForm(initialData: ConnectionFormData) {
    private var selectedDatabaseType = initialData.databaseType
    private val databaseTypeComboBox =
        ComboBox(DatabaseType.entries.toTypedArray()).apply {
            selectedItem = selectedDatabaseType
        }
    private val nameField = JTextField(initialData.name)
    private val hostField = JTextField(initialData.host)
    private val portField = JTextField(initialData.port.toString())
    private val databaseField = JTextField(initialData.database)
    private val usernameField = JTextField(initialData.username)
    private val passwordField = JPasswordField(initialData.password ?: "")
    private val savePasswordCheckbox = JCheckBox("Save password", initialData.savePassword)
    private val schemaField = JTextField(initialData.schema)
    private var schemaRow: Row? = null

    init {
        databaseTypeComboBox.addActionListener {
            val newType = databaseTypeComboBox.selectedItem as DatabaseType
            applyDefaultsForTypeChange(selectedDatabaseType, newType)
            selectedDatabaseType = newType
            syncSchemaRowVisibility()
        }
    }

    companion object {
        private const val REQUIRED = "Field must be not empty"
        private const val COLUMN_SIZE = 15
    }

    @Suppress("unchecked_cast")
    private fun <T : JComponent> Cell<T>.required() {
        assert(this.component is JTextField)
        this as Cell<JTextField>
        this.validationOnApply { if (it.text.isEmpty()) ValidationInfo(REQUIRED) else null }
    }

    fun createPanel(): JComponent =
        panel {
            row("Database Engine:") {
                cell(databaseTypeComboBox)
                    .columns(COLUMN_SIZE)
            }
            row("Connection Name:") {
                cell(nameField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Host:") {
                cell(hostField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Port:") {
                cell(portField)
                    .columns(COLUMN_SIZE)
                    .validationOnApply {
                        runCatching { it.text.toInt() }
                            .fold(
                                onSuccess = { null },
                                onFailure = { ValidationInfo("Only integers allowed") },
                            )
                    }
            }
            row("Database:") {
                cell(databaseField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            schemaRow =
                row("Schema:") {
                    cell(schemaField)
                        .columns(COLUMN_SIZE)
                        .required()
                }
            row("Username:") {
                cell(usernameField)
                    .columns(COLUMN_SIZE)
                    .required()
            }
            row("Password:") {
                cell(passwordField)
                    .columns(COLUMN_SIZE)
            }
            row {
                cell(savePasswordCheckbox)
            }
        }.apply {
            syncSchemaRowVisibility()
        }

    fun getData(): ConnectionFormData =
        ConnectionFormData(
            name = nameField.text.trim(),
            host = hostField.text.trim(),
            port = portField.text.trim().toInt(),
            database = databaseField.text.trim(),
            databaseType = selectedDatabaseType,
            username = usernameField.text.trim(),
            schema =
                when (selectedDatabaseType) {
                    DatabaseType.MYSQL -> databaseField.text.trim()
                    DatabaseType.POSTGRESQL -> schemaField.text.trim()
                },
            password = String(passwordField.password).ifEmpty { null },
            savePassword = savePasswordCheckbox.isSelected,
        )

    private fun applyDefaultsForTypeChange(oldType: DatabaseType, newType: DatabaseType) {
        if (oldType == newType) return
        val oldDefaults = DatabaseAdapters.defaults(oldType)
        val newDefaults = DatabaseAdapters.defaults(newType)
        replaceIfDefault(hostField, oldDefaults.host, newDefaults.host)
        replaceIfDefault(portField, oldDefaults.port.toString(), newDefaults.port.toString())
        replaceIfDefault(databaseField, oldDefaults.database, newDefaults.database)
        replaceIfDefault(schemaField, oldDefaults.schema, newDefaults.schema)
        replaceIfDefault(usernameField, oldDefaults.username, newDefaults.username)
    }

    private fun replaceIfDefault(
        field: JTextField,
        oldValue: String,
        newValue: String,
    ) {
        if (field.text.trim().isEmpty() || field.text.trim() == oldValue) {
            field.text = newValue
        }
    }

    private fun syncSchemaRowVisibility() {
        schemaRow?.visible(selectedDatabaseType != DatabaseType.MYSQL)
    }
}
