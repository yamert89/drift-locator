package com.github.yamert89.mysql

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager

internal class MysqlSchemaComparatorIntegrationTest : MysqlIntegrationTestBase() {
    @Test
    fun `testConnection should connect successfully`() {
        val result =
            MysqlConnectionTester.testConnection(
                host = mysql.host,
                port = mysql.firstMappedPort,
                database = mysql.databaseName,
                username = mysql.username,
                password = mysql.password,
            )

        assertTrue(result.getOrThrow())
    }

    @Test
    fun `fetch schema should return empty when no schema objects`() {
        newConnection().use { connection ->
            val schema = MysqlSchemaFetcher.fetchSchema(connection, "testdb")

            assertTrue(
                schema.objects.isEmpty(),
                "Expected no objects on mysql:$mysqlVersion, found: ${schema.objects.map { it.type to it.name }}",
            )
        }
    }

    @Test
    fun `fetch schema should include table columns indexes and constraints`() {
        newConnection().use { connection ->
            connection.createStatement().execute(
                """
                CREATE TABLE departments (
                    id INT PRIMARY KEY,
                    name VARCHAR(100) UNIQUE
                )
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE TABLE employees (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    department_id INT,
                    email VARCHAR(100) NOT NULL,
                    email_domain VARCHAR(100) GENERATED ALWAYS AS (substring_index(email, '@', -1)) STORED,
                    salary INT DEFAULT 100 CHECK (salary >= 0),
                    CONSTRAINT fk_department FOREIGN KEY (department_id) REFERENCES departments(id)
                )
                """.trimIndent(),
            )
            connection.createStatement().execute("CREATE UNIQUE INDEX idx_employees_email ON employees(email)")

            val schema = MysqlSchemaFetcher.fetchSchema(connection, "testdb")
            val employees = schema.objects.filterIsInstance<MysqlTable>().first { it.mysqlObjectName == "employees" }

            assertEquals("testdb.employees", employees.name)
            assertEquals("InnoDB", employees.engine)
            assertNotNull(employees.columns.find { it.columnName == "email_domain" }?.generationExpression)
            assertFalse(employees.columns.first { it.columnName == "email" }.isNullable)
            assertEquals("100", employees.columns.first { it.columnName == "salary" }.defaultValue)
            assertNotNull(employees.indexes.find { it.indexName == "idx_employees_email" && it.isUnique })
            assertNotNull(employees.constraints.find { it.constraintType == "PRIMARY KEY" })
            assertNotNull(employees.constraints.find { it.constraintName == "fk_department" })
            assertNotNull(employees.constraints.find { it.constraintType == "CHECK" })
        }
    }

    @Test
    fun `fetch schema should include views routines parameters triggers events and partitions`() {
        newConnection().use { connection ->
            connection.createStatement().execute(
                """
                CREATE TABLE audit_log (
                    id INT PRIMARY KEY AUTO_INCREMENT,
                    action VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent(),
            )
            connection.createStatement().execute("CREATE VIEW audit_view AS SELECT id, action FROM audit_log")
            connection.createStatement().execute(
                """
                CREATE FUNCTION echo_action(action_text VARCHAR(100))
                RETURNS VARCHAR(100)
                DETERMINISTIC
                NO SQL
                RETURN action_text
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE PROCEDURE add_audit(IN action_text VARCHAR(100))
                INSERT INTO audit_log(action) VALUES (action_text)
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE TRIGGER audit_before_insert
                BEFORE INSERT ON audit_log
                FOR EACH ROW SET NEW.action = COALESCE(NEW.action, 'created')
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE EVENT cleanup_audit
                ON SCHEDULE EVERY 1 DAY
                DO DELETE FROM audit_log WHERE created_at < NOW() - INTERVAL 30 DAY
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                CREATE TABLE partitioned_orders (
                    id INT NOT NULL,
                    created_year INT NOT NULL,
                    PRIMARY KEY(id, created_year)
                )
                PARTITION BY RANGE (created_year) (
                    PARTITION p2024 VALUES LESS THAN (2025),
                    PARTITION pmax VALUES LESS THAN MAXVALUE
                )
                """.trimIndent(),
            )

            val schema = MysqlSchemaFetcher.fetchSchema(connection, "testdb")

            val view = schema.objects.filterIsInstance<MysqlView>().single()
            assertEquals("testdb.audit_view", view.name)
            assertEquals(2, view.columns.size)

            val function = schema.objects.filterIsInstance<MysqlRoutine>().first { it.type == "FUNCTION" }
            assertEquals("echo_action", function.mysqlObjectName)
            assertEquals("varchar", function.returnType)
            assertEquals(1, function.parameters.size)

            val procedure = schema.objects.filterIsInstance<MysqlRoutine>().first { it.type == "PROCEDURE" }
            assertEquals("add_audit", procedure.mysqlObjectName)
            assertEquals("IN", procedure.parameters.single().mode)

            val trigger = schema.objects.filterIsInstance<MysqlTrigger>().single()
            assertEquals("audit_log", trigger.tableName)
            assertEquals("BEFORE", trigger.timing)

            val event = schema.objects.filterIsInstance<MysqlEvent>().single()
            assertEquals("cleanup_audit", event.mysqlObjectName)
            assertEquals("ENABLED", event.status)

            val partitions = schema.objects.filterIsInstance<MysqlPartition>()
            assertEquals(2, partitions.size)
            assertTrue(partitions.any { it.mysqlObjectName == "p2024" })
        }
    }

    @Test
    fun `fetch schema should include schema grants users and tablespaces when fetching all schemas`() {
        newConnection().use { connection ->
            connection.createStatement().execute("CREATE TABLE metadata_probe (id INT)")
        }

        DriverManager.getConnection(mysql.jdbcUrl, "root", mysql.password).use { connection ->
            val schema = MysqlSchemaFetcher.fetchSchema(connection)

            assertTrue(schema.objects.filterIsInstance<MysqlSchemaObject>().any { it.schemaName == "testdb" })
            assertTrue(schema.objects.filterIsInstance<MysqlGrant>().isNotEmpty())
            assertTrue(schema.objects.filterIsInstance<MysqlUser>().isNotEmpty())
            assertTrue(schema.objects.filterIsInstance<MysqlTablespace>().isNotEmpty())
        }
    }

    @Test
    fun `compare different schemas should detect added table`() {
        newConnection().use { connection ->
            val source = MysqlSchemaFetcher.fetchSchema(connection, "testdb")
            connection.createStatement().execute("CREATE TABLE added_table (id INT)")
            val target = MysqlSchemaFetcher.fetchSchema(connection, "testdb")

            val diff = MysqlSchemaComparator().compare(source, target)

            assertEquals(1, diff.added.size)
            assertEquals("testdb.added_table", diff.added.first().name)
        }
    }
}
