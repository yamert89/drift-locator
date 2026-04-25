package com.github.yamert89.mysql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MysqlMetaTest {
    @Test
    fun `getDefaults should return correct MySQL defaults`() {
        val defaults = MysqlMeta().getDefaults()

        assertEquals("localhost", defaults.host)
        assertEquals(3306, defaults.port)
        assertEquals("mysql", defaults.database)
        assertEquals("mysql", defaults.schema)
        assertEquals("root", defaults.username)
    }
}
