package com.github.yamert89.postgresql

import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager

private const val DEFAULT_POSTGRES_VERSION = "15"

internal object PostgresIntegrationTestContainer {
    val postgresVersion: String = System.getProperty("postgresVersion", DEFAULT_POSTGRES_VERSION)

    val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:$postgresVersion")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .apply { start() }
    }

    fun newConnection(): Connection = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
}

internal abstract class PostgresIntegrationTestBase {
    protected val postgres = PostgresIntegrationTestContainer.container
    protected val postgresVersion = PostgresIntegrationTestContainer.postgresVersion

    @BeforeEach
    fun cleanDatabase() {
        PostgresIntegrationTestContainer.newConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    DO $$
                    BEGIN
                        IF EXISTS (SELECT 1 FROM pg_subscription WHERE subname = 'sub_masked') THEN
                            ALTER SUBSCRIPTION sub_masked DISABLE;
                            ALTER SUBSCRIPTION sub_masked SET (slot_name = NONE);
                            DROP SUBSCRIPTION sub_masked;
                        END IF;
                    END
                    $$;
                    """.trimIndent(),
                )
                statement.execute("DROP SERVER IF EXISTS loopback_server CASCADE")
                statement.execute("DROP EXTENSION IF EXISTS postgres_fdw CASCADE")
                statement.execute("DROP PUBLICATION IF EXISTS pub_all")
                statement.execute("DROP EXTENSION IF EXISTS hstore CASCADE")
                statement.execute("DROP SCHEMA IF EXISTS ext_schema CASCADE")
                statement.execute("DROP ROLE IF EXISTS app_reader")
                statement.execute("DROP TABLESPACE IF EXISTS driftlocator_ts")
                statement.execute("DROP SCHEMA IF EXISTS public CASCADE")
                statement.execute("CREATE SCHEMA public")
                statement.execute("GRANT ALL ON SCHEMA public TO ${postgres.username}")
            }
        }
    }

    protected fun newConnection(): Connection = PostgresIntegrationTestContainer.newConnection()
}
