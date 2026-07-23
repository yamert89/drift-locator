package com.github.yamert89.mysql

import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.MySQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

private const val DEFAULT_MYSQL_VERSION = "8.0"

internal object MysqlIntegrationTestContainer {
    val mysqlVersion: String = System.getProperty("mysqlVersion", DEFAULT_MYSQL_VERSION)

    val container: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:$mysqlVersion")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withConfigurationOverride("mysql-test-conf")
            .withCommand("--log-bin-trust-function-creators=1")
            .apply { start() }
    }

    fun newConnection(): Connection = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
}

internal abstract class MysqlIntegrationTestBase {
    protected val mysql = MysqlIntegrationTestContainer.container
    protected val mysqlVersion = MysqlIntegrationTestContainer.mysqlVersion

    @BeforeEach
    fun cleanDatabase() {
        MysqlIntegrationTestContainer.newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                dropObjects(statement, "TRIGGER", "information_schema.triggers", "trigger_schema", "trigger_name")
                dropObjects(statement, "EVENT", "information_schema.events", "event_schema", "event_name")
                dropRoutines(statement, "FUNCTION")
                dropRoutines(statement, "PROCEDURE")
                dropObjects(statement, "VIEW", "information_schema.views", "table_schema", "table_name")
                dropObjects(
                    statement,
                    "TABLE",
                    "information_schema.tables",
                    "table_schema",
                    "table_name",
                    "AND table_type = 'BASE TABLE'",
                )
                statement.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        }
    }

    protected fun newConnection(): Connection = MysqlIntegrationTestContainer.newConnection()

    private fun dropObjects(
        statement: Statement,
        objectType: String,
        table: String,
        schemaColumn: String,
        nameColumn: String,
        extraWhere: String = "",
    ) {
        val resultSet =
            statement.executeQuery(
                """
                SELECT $nameColumn AS object_name
                FROM $table
                WHERE $schemaColumn = 'testdb' $extraWhere
                """.trimIndent(),
            )
        val names =
            buildList {
                while (resultSet.next()) add(resultSet.getString("object_name"))
            }
        resultSet.close()
        names.forEach { name -> statement.execute("DROP $objectType IF EXISTS `testdb`.`$name`") }
    }

    private fun dropRoutines(statement: Statement, routineType: String) {
        val resultSet =
            statement.executeQuery(
                """
                SELECT routine_name
                FROM information_schema.routines
                WHERE routine_schema = 'testdb' AND routine_type = '$routineType'
                """.trimIndent(),
            )
        val names =
            buildList {
                while (resultSet.next()) add(resultSet.getString("routine_name"))
            }
        resultSet.close()
        names.forEach { name -> statement.execute("DROP $routineType IF EXISTS `testdb`.`$name`") }
    }
}
