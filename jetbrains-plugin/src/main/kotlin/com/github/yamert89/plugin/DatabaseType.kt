package com.github.yamert89.plugin

import kotlinx.serialization.Serializable

@Serializable
enum class DatabaseType(val displayName: String) {
    POSTGRESQL("PostgreSQL"),
    MYSQL("MySQL"),
    ;

    override fun toString(): String = displayName
}
