package com.github.yamert89.postgresql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for PgMeta class.
 */
class PgMetaTest {

    @Test
    fun `getDefaults should return correct PostgreSQL defaults`() {
        val pgMeta = PgMeta()
        val defaults = pgMeta.getDefaults()

        assertEquals(5432, defaults.port)
        assertEquals("postgres", defaults.database)
        assertEquals("public", defaults.schema)
        assertEquals("postgres", defaults.username)
    }

    @Test
    fun `getDefaults should inherit host default from Defaults data class`() {
        val pgMeta = PgMeta()
        val defaults = pgMeta.getDefaults()

        assertEquals("localhost", defaults.host)
    }
}
