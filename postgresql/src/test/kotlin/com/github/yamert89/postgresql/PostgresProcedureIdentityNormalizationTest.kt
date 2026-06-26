package com.github.yamert89.postgresql

import kotlin.test.Test
import kotlin.test.assertEquals

class PostgresProcedureIdentityNormalizationTest {
    @Test
    fun `normalize procedure identity arguments should strip IN modifiers`() {
        assertEquals(
            "arg_one integer, arg_two text",
            normalizeProcedureIdentityArguments("IN arg_one integer, IN arg_two text"),
        )
    }

    @Test
    fun `procedure name should use normalized identity arguments`() {
        val procedure =
            PostgresProcedure(
                schema = "public",
                pgObjectName = "log_action",
                identityArguments = normalizeProcedureIdentityArguments("IN action_text character varying"),
                arguments = "IN action_text character varying",
                language = "sql",
                definition = "CREATE OR REPLACE PROCEDURE",
            )

        assertEquals("public.log_action(action_text character varying)", procedure.name)
        assertEquals("log_action(action_text character varying)", procedure.objectName)
    }
}
