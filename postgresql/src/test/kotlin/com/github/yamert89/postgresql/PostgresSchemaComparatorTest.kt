package com.github.yamert89.postgresql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers
class PostgresSchemaComparatorTest {
    companion object {
        @Container
        val postgres =
            PostgreSQLContainer("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
    }

    @Test
    fun `fetch schema should return empty when no objects`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        val schema = PostgresSchemaComparator.fetchSchema(connection)
        assertTrue(schema.objects.isEmpty())
        connection.close()
    }

    @Test
    fun `fetch schema should include created table`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE test_table (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL
            )
            """.trimIndent(),
        )

        val schema = PostgresSchemaComparator.fetchSchema(connection)
        val tables = schema.objects.filter { it.type == "TABLE" }
        assertEquals(1, tables.size)
        val table = tables.first() as PostgresTable
        assertEquals("public.test_table", table.name)
        assertEquals(2, table.columns.size)
        val idColumn = table.columns.find { it.columnName == "id" }
        assertNotNull(idColumn)
        assertEquals("integer", idColumn?.dataType)
        assertFalse(idColumn?.isNullable ?: true)
        val nameColumn = table.columns.find { it.columnName == "name" }
        assertNotNull(nameColumn)
        assertEquals("character varying", nameColumn?.dataType)
        assertFalse(nameColumn?.isNullable ?: true)

        // Check primary key constraint
        val pkConstraint = table.constraints.find { it.constraintType == "PRIMARY KEY" }
        assertNotNull(pkConstraint)

        connection.close()
    }

    @Test
    fun `compare identical schemas should have no differences`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE test_table (
                id SERIAL PRIMARY KEY
            )
            """.trimIndent(),
        )

        val schema = PostgresSchemaComparator.fetchSchema(connection)
        val comparator = PostgresSchemaComparator(connection)
        val diff = comparator.compare(schema, schema)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.modified.isEmpty())
        connection.close()
    }

    @Test
    fun `compare different schemas should detect added table`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        // Source schema empty
        val source = PostgresSchemaComparator.fetchSchema(connection)

        // Create a table
        connection.createStatement().execute(
            """
            CREATE TABLE added_table (id INT)
            """.trimIndent(),
        )
        val target = PostgresSchemaComparator.fetchSchema(connection)

        val comparator = PostgresSchemaComparator(connection)
        val diff = comparator.compare(source, target)
        assertEquals(1, diff.added.size)
        assertEquals("public.added_table", diff.added.first().name)
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.modified.isEmpty())
        connection.close()
    }

    @Test
    fun `compare different schemas should detect removed table`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        // Create a table
        connection.createStatement().execute(
            """
            CREATE TABLE removed_table (id INT)
            """.trimIndent(),
        )
        val source = PostgresSchemaComparator.fetchSchema(connection)

        // Drop table
        connection.createStatement().execute("DROP TABLE removed_table")
        val target = PostgresSchemaComparator.fetchSchema(connection)

        val comparator = PostgresSchemaComparator(connection)
        val diff = comparator.compare(source, target)
        assertEquals(1, diff.removed.size)
        assertEquals("public.removed_table", diff.removed.first().name)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.modified.isEmpty())
        connection.close()
    }

    @Test
    fun `compare different schemas should detect modified table`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        // Create table with one column
        connection.createStatement().execute(
            """
            CREATE TABLE modified_table (id INT)
            """.trimIndent(),
        )
        val source = PostgresSchemaComparator.fetchSchema(connection)

        // Add a column
        connection.createStatement().execute(
            """
            ALTER TABLE modified_table ADD COLUMN name VARCHAR(100)
            """.trimIndent(),
        )
        val target = PostgresSchemaComparator.fetchSchema(connection)

        val comparator = PostgresSchemaComparator(connection)
        val diff = comparator.compare(source, target)
        assertTrue(diff.added.isEmpty())
        assertTrue(diff.removed.isEmpty())
        assertEquals(1, diff.modified.size)
        val (oldObj, newObj) = diff.modified.first()
        assertEquals("public.modified_table", oldObj.name)
        assertEquals("public.modified_table", newObj.name)
        val oldTable = oldObj as PostgresTable
        val newTable = newObj as PostgresTable
        assertEquals(1, oldTable.columns.size)
        assertEquals(2, newTable.columns.size)
        connection.close()
    }

    @Test
    fun `export diff to file`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        // Create two different schemas
        connection.createStatement().execute("CREATE TABLE table1 (id INT)")
        val source = PostgresSchemaComparator.fetchSchema(connection)
        connection.createStatement().execute("CREATE TABLE table2 (name VARCHAR(100))")
        val target = PostgresSchemaComparator.fetchSchema(connection)

        val comparator = PostgresSchemaComparator(connection)
        val diff = comparator.compare(source, target)

        // Export to temporary file
        val tempFile = java.nio.file.Files.createTempFile("diff", ".txt")
        com.github.yamert89.core.DiffExporter.exportToFile(diff, tempFile)

        assertTrue(tempFile.toFile().exists())
        val content = tempFile.toFile().readText()
        assertTrue(content.contains("Added Objects"))
        assertTrue(content.contains("table2"))
        assertTrue(content.contains("Removed Objects"))
        assertTrue(content.contains("table1"))

        // Cleanup
        tempFile.toFile().delete()
        connection.close()
    }
}
