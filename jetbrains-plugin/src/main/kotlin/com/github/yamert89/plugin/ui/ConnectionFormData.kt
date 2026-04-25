package com.github.yamert89.plugin.ui

import com.github.yamert89.core.Defaults
import com.github.yamert89.plugin.DatabaseConnection
import com.github.yamert89.plugin.DatabaseType

data class ConnectionFormData(
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val databaseType: DatabaseType,
    val username: String,
    val schema: String,
    val password: String? = null,
    val savePassword: Boolean = true,
) {
    fun toDatabaseConnection(): DatabaseConnection =
        DatabaseConnection(
            id = name,
            name = name,
            host = host,
            port = port,
            database = database,
            databaseType = databaseType,
            username = username,
            schema = schema,
            savePassword = savePassword,
        ).withPassword(password)

    companion object {
        fun forNewConnection(
            defaults: Defaults,
            lastConnection: DatabaseConnection? = null,
        ): ConnectionFormData =
            ConnectionFormData(
                name = "",
                host = lastConnection?.host ?: defaults.host,
                port = lastConnection?.port ?: defaults.port,
                database = lastConnection?.database ?: defaults.database,
                databaseType = lastConnection?.databaseType ?: DatabaseType.POSTGRESQL,
                username = lastConnection?.username ?: defaults.username,
                schema = lastConnection?.schema ?: defaults.schema,
                savePassword = true,
            )

        fun fromConnection(connection: DatabaseConnection): ConnectionFormData =
            ConnectionFormData(
                name = connection.name,
                host = connection.host,
                port = connection.port,
                database = connection.database,
                databaseType = connection.databaseType,
                username = connection.username,
                schema = connection.schema,
                password = connection.password,
                savePassword = connection.savePassword,
            )
    }
}
