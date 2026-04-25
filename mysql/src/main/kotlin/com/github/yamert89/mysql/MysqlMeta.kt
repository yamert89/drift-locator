package com.github.yamert89.mysql

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults

class MysqlMeta : DatabaseMeta {
    override fun getDefaults() =
        Defaults(
            port = 3306,
            database = "mysql",
            schema = "mysql",
            username = "root",
        )
}
