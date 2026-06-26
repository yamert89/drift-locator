package com.github.yamert89.sqlite

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager

internal class SqliteSchemaComparatorIntegrationTest {
    @Test
    fun `fetch schema should include tables columns indexes foreign keys views and triggers`() {
        sqliteConnection().use { connection ->
            connection.createStatement().execute("PRAGMA foreign_keys = ON")
            connection.createStatement().execute(
                """
                CREATE TABLE departments (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE
                )
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE TABLE employees (
                    id INTEGER PRIMARY KEY,
                    department_id INTEGER NOT NULL,
                    email TEXT NOT NULL,
                    salary INTEGER DEFAULT 100 CHECK (salary >= 0),
                    email_domain TEXT GENERATED ALWAYS AS (substr(email, instr(email, '@') + 1)) STORED,
                    FOREIGN KEY(department_id) REFERENCES departments(id) ON DELETE CASCADE
                ) STRICT
                """.trimIndent(),
            )
            connection.createStatement().execute("CREATE UNIQUE INDEX idx_employees_email ON employees(email)")
            connection.createStatement().execute(
                "CREATE INDEX idx_employees_domain_expr ON employees(substr(email, 1, 3)) WHERE email IS NOT NULL",
            )
            connection.createStatement().execute("CREATE VIEW employee_view AS SELECT id, email FROM employees")
            connection.createStatement().execute(
                """
                CREATE TRIGGER employees_ai
                AFTER INSERT ON employees
                BEGIN
                    UPDATE employees SET salary = COALESCE(NEW.salary, 100) WHERE id = NEW.id;
                END
                """.trimIndent(),
            )

            val schema = SqliteSchemaFetcher.fetchSchema(connection)

            val table = schema.objects.filterIsInstance<SqliteTable>().first { it.sqliteObjectName == "employees" }
            assertTrue(table.strict)
            assertTrue(table.createSql.contains("CHECK"))
            assertFalse(table.columns.first { it.columnName == "email" }.isNullable)
            assertEquals(100.toString(), table.columns.first { it.columnName == "salary" }.defaultValue)
            assertTrue(table.columns.any { it.columnName == "email_domain" && it.hidden > 0 })
            assertTrue(table.indexes.any { it.sqliteObjectName == "idx_employees_email" && it.isUnique })
            assertTrue(table.indexes.any { it.sqliteObjectName == "idx_employees_domain_expr" && it.isPartial })
            assertTrue(table.foreignKeys.any { it.referencedTable == "departments" && it.onDelete == "CASCADE" })
            assertNotNull(schema.objects.filterIsInstance<SqliteView>().singleOrNull())
            assertNotNull(schema.objects.filterIsInstance<SqliteTrigger>().singleOrNull())
        }
    }

    @Test
    fun `compare different schemas should detect sqlite additions and ddl modifications`() {
        val sourceFile = Files.createTempFile("drift-locator-source-", ".db")
        val targetFile = Files.createTempFile("drift-locator-target-", ".db")

        DriverManager.getConnection("jdbc:sqlite:${sourceFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT)")
        }
        DriverManager.getConnection("jdbc:sqlite:${targetFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().execute("CREATE TABLE sample (id INTEGER PRIMARY KEY, name TEXT CHECK(length(name) > 1)) STRICT")
            connection.createStatement().execute("CREATE TABLE added_table (id INTEGER PRIMARY KEY)")
            connection.createStatement().execute("CREATE VIEW sample_view AS SELECT id FROM sample")
        }

        val sourceSchema =
            SqliteConnectionManager
                .getConnection(sourceFile.toString())
                .use(SqliteSchemaFetcher::fetchSchema)
        val targetSchema =
            SqliteConnectionManager
                .getConnection(targetFile.toString())
                .use(SqliteSchemaFetcher::fetchSchema)

        val diff = SqliteSchemaComparator().compare(sourceSchema, targetSchema)

        assertTrue(diff.added.any { it.name == "added_table" })
        assertTrue(diff.added.any { it.name == "sample_view" })
        assertTrue(
            diff.modified.any { (oldObj, newObj) ->
                oldObj.name == "sample" &&
                    newObj is SqliteTable &&
                    newObj.createSql.contains("STRICT")
            },
        )
    }

    @Test
    fun `fetch schema should ignore internal sqlite objects`() {
        sqliteConnection().use { connection ->
            connection.createStatement().execute("CREATE TABLE sample (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)")

            val schema = SqliteSchemaFetcher.fetchSchema(connection)

            assertTrue(schema.objects.none { it.name.startsWith("sqlite_") })
        }
    }

    private fun sqliteConnection(): Connection {
        val file = Files.createTempFile("drift-locator-", ".db")
        return DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")
    }
}
