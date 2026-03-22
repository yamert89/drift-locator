package com.github.yamert89.postgresql

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults

class PgMeta: DatabaseMeta {
    override fun getDefaults() = Defaults(
        port = 5432,
        database = "postgres",
        schema = "public",
        username = "postgres",
    )
}