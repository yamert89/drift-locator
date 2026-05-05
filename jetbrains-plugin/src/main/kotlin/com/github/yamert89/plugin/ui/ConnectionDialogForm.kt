package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DatabaseAdapters
import com.github.yamert89.plugin.DatabaseType
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
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
    private val filePathField =
        TextFieldWithBrowseButton().apply {
            text = initialData.filePath
            addBrowseFolderListener(
                "Select SQLite Database",
                "Choose an existing SQLite database file",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
        }
    private var schemaRow: Row? = null
    private var hostRow: Row? = null
    private var portRow: Row? = null
    private var databaseRow: Row? = null
    private var usernameRow: Row? = null
    private var passwordRow: Row? = null
    private var savePasswordRow: Row? = null
    private var filePathRow: Row? = null

    init {
        databaseTypeComboBox.addActionListener {
            val newType = databaseTypeComboBox.selectedItem as DatabaseType
            applyDefaultsForTypeChange(selectedDatabaseType, newType)
            selectedDatabaseType = newType
            syncFieldVisibility()
        }
    }

    companion object {
        private const val REQUIRED = "Field must be not empty"
        private const val COLUMN_SIZE = 15
    }

    @Suppress("unchecked_cast")
    private fun <T : JComponent> Cell<T>.required(enabled: () -> Boolean = { true }) {
        assert(this.component is JTextField)
        this as Cell<JTextField>
        this.validationOnApply {
            if (!enabled()) {
                null
            } else if (it.text.isEmpty()) {
                ValidationInfo(REQUIRED)
            } else {
                null
            }
        }
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
            hostRow =
                row("Host:") {
                    cell(hostField)
                        .columns(COLUMN_SIZE)
                        .required { selectedDatabaseType != DatabaseType.SQLITE }
                }
            portRow =
                row("Port:") {
                    cell(portField)
                        .columns(COLUMN_SIZE)
                        .validationOnApply {
                            if (selectedDatabaseType == DatabaseType.SQLITE) return@validationOnApply null
                            runCatching { it.text.toInt() }
                                .fold(
                                    onSuccess = { null },
                                    onFailure = { ValidationInfo("Only integers allowed") },
                                )
                        }
                }
            databaseRow =
                row("Database:") {
                    cell(databaseField)
                        .columns(COLUMN_SIZE)
                        .required { selectedDatabaseType != DatabaseType.SQLITE }
                }
            schemaRow =
                row("Schema:") {
                    cell(schemaField)
                        .columns(COLUMN_SIZE)
                        .validationOnApply {
                            if (selectedDatabaseType == DatabaseType.SQLITE) {
                                null
                            } else if (it.text.isEmpty()) {
                                ValidationInfo(REQUIRED)
                            } else {
                                null
                            }
                        }
                }
            usernameRow =
                row("Username:") {
                    cell(usernameField)
                        .columns(COLUMN_SIZE)
                        .required { selectedDatabaseType != DatabaseType.SQLITE }
                }
            passwordRow =
                row("Password:") {
                    cell(passwordField)
                        .columns(COLUMN_SIZE)
                }
            savePasswordRow =
                row {
                    cell(savePasswordCheckbox)
                }
            filePathRow =
                row("SQLite File:") {
                    cell(filePathField)
                        .columns(COLUMN_SIZE)
                        .validationOnApply {
                            validateSqliteFilePath(filePathField.text.trim())
                        }
                }
        }.apply {
            syncFieldVisibility()
        }

    fun getData(): ConnectionFormData =
        ConnectionFormData(
            name = nameField.text.trim(),
            host = hostField.text.trim(),
            port = portField.text.trim().toIntOrNull() ?: 0,
            database = databaseField.text.trim(),
            databaseType = selectedDatabaseType,
            username = usernameField.text.trim(),
            schema =
                when (selectedDatabaseType) {
                    DatabaseType.SQLITE -> ""
                    DatabaseType.MYSQL -> databaseField.text.trim()
                    DatabaseType.POSTGRESQL -> schemaField.text.trim()
                },
            filePath = if (selectedDatabaseType == DatabaseType.SQLITE) filePathField.text.trim() else "",
            password = String(passwordField.password).ifEmpty { null },
            savePassword = if (selectedDatabaseType == DatabaseType.SQLITE) false else savePasswordCheckbox.isSelected,
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
        replaceIfDefault(filePathField.textField, oldDefaults.filePath, newDefaults.filePath)
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

    private fun syncFieldVisibility() {
        val isSqlite = selectedDatabaseType == DatabaseType.SQLITE
        hostRow?.visible(!isSqlite)
        portRow?.visible(!isSqlite)
        databaseRow?.visible(!isSqlite)
        usernameRow?.visible(!isSqlite)
        passwordRow?.visible(!isSqlite)
        savePasswordRow?.visible(!isSqlite)
        filePathRow?.visible(isSqlite)
        schemaRow?.visible(selectedDatabaseType == DatabaseType.POSTGRESQL)
    }

    private fun validateSqliteFilePath(filePath: String): ValidationInfo? {
        if (selectedDatabaseType != DatabaseType.SQLITE) {
            return null
        }
        return when {
            filePath.isBlank() -> ValidationInfo(REQUIRED)
            else -> {
                val path = runCatching { Path.of(filePath) }.getOrNull()
                when {
                    path == null -> ValidationInfo("Invalid file path")
                    !Files.exists(path) -> ValidationInfo("SQLite database file does not exist")
                    !Files.isRegularFile(path) -> ValidationInfo("SQLite database path must point to a file")
                    !Files.isReadable(path) -> ValidationInfo("SQLite database file is not readable")
                    else -> null
                }
            }
        }
    }
}
