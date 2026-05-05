package com.github.yamert89.core

interface DatabaseMeta {
    fun getDefaults(): Defaults
}

data class Defaults(
    val host: String = "localhost",
    val port: Int = 0,
    val database: String = "",
    val schema: String = "",
    val username: String = "",
    val filePath: String = "",
)
