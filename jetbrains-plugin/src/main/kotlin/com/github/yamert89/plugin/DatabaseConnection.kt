package com.github.yamert89.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Database connection configuration.
 * The password is NOT serialized to JSON - it's stored securely via PasswordStorage.
 */
@Serializable
data class DatabaseConnection(
    val id: String,
    val name: String,
    val host: String = "",
    val port: Int = 0,
    val database: String = "",
    val databaseType: DatabaseType = DatabaseType.POSTGRESQL,
    val username: String = "",
    val schema: String = "",
    val filePath: String = "",
    val savePassword: Boolean = true,
    val isValid: Boolean = true,
) {
    /**
     * The password is stored separately in PasswordSafe (encrypted OS keychain).
     * This field is not serialized to JSON.
     */
    @Transient
    var password: String? = null
        get() = field ?: PasswordStorage.getPassword(id)

    val url: String
        get() = DatabaseAdapters.forType(databaseType).jdbcUrl(this)

    val usesFilePath: Boolean
        get() = databaseType == DatabaseType.SQLITE

    val requiresPassword: Boolean
        get() = !usesFilePath

    fun displayScope(): String? =
        when (databaseType) {
            DatabaseType.MYSQL -> database.ifBlank { null }
            DatabaseType.POSTGRESQL -> schema.ifBlank { null }
            DatabaseType.SQLITE -> filePath.ifBlank { null }
        }

    /**
     * Returns a copy of this connection with the password explicitly set.
     * Used when creating/updating connections from UI dialogs.
     */
    fun withPassword(password: String?): DatabaseConnection =
        this.copy().apply {
            this.password = password
        }
}
