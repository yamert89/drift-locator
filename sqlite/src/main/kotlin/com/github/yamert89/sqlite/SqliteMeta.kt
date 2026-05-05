package com.github.yamert89.sqlite

import com.github.yamert89.core.DatabaseMeta
import com.github.yamert89.core.Defaults

class SqliteMeta : DatabaseMeta {
    override fun getDefaults(): Defaults = Defaults(filePath = "")
}
