package com.github.yamert89.plugin.ui

import com.github.yamert89.plugin.DatabaseAdapters
import com.github.yamert89.plugin.DatabaseType
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.observable.util.notEqualsTo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JTextField

class ConnectionDialogForm(initialData: ConnectionFormData) {
    private val propertyGraph = PropertyGraph()
    private val selectedDatabaseTypeProperty = propertyGraph.property(initialData.databaseType)
    private val selectedDatabaseType: DatabaseType
        get() = selectedDatabaseTypeProperty.get()
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
    private val sqliteFileChooserDescriptor =
        FileChooserDescriptor(true, false, false, false, false, false).apply {
            title = "Select SQLite Database"
            description = "Choose an existing SQLite database file"
        }
            //.withExtensionFilter("DB", "db") //todo up minimal version to 2024.3 for this line
    private val filePathField =
        TextFieldWithBrowseButton().apply {
            text = initialData.filePath
            addBrowseFolderListener(TextBrowseFolderListener(sqliteFileChooserDescriptor))
        }

    init {
        databaseTypeComboBox.addActionListener {
            val newType = databaseTypeComboBox.selectedItem as DatabaseType
            applyDefaultsForTypeChange(selectedDatabaseType, newType)
            selectedDatabaseTypeProperty.set(newType)
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
            row("Host:") {
                cell(hostField)
                    .columns(COLUMN_SIZE)
                    .required { selectedDatabaseType != DatabaseType.SQLITE }
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
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
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
            row("Database:") {
                cell(databaseField)
                    .columns(COLUMN_SIZE)
                    .required { selectedDatabaseType != DatabaseType.SQLITE }
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
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
            }.visibleIf(selectedDatabaseTypeProperty.equalsTo(DatabaseType.POSTGRESQL))
            row("Username:") {
                cell(usernameField)
                    .columns(COLUMN_SIZE)
                    .required { selectedDatabaseType != DatabaseType.SQLITE }
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
            row("Password:") {
                cell(passwordField)
                    .columns(COLUMN_SIZE)
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
            row {
                cell(savePasswordCheckbox)
            }.visibleIf(selectedDatabaseTypeProperty.notEqualsTo(DatabaseType.SQLITE))
            row("SQLite File:") {
                cell(filePathField)
                    .columns(COLUMN_SIZE)
                    .validationOnApply {
                        validateSqliteFilePath(filePathField.text.trim())
                    }
            }.visibleIf(selectedDatabaseTypeProperty.equalsTo(DatabaseType.SQLITE))
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
