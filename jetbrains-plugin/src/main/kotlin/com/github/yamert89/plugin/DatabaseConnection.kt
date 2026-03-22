package com.github.yamert89.plugin

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseConnection(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String? = null,
    val schema: String,
) {
    val url: String
        get() = "jdbc:postgresql://$host:$port/$database"
}
