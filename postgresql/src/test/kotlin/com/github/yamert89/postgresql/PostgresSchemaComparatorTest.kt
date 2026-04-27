package com.github.yamert89.postgresql

import com.github.yamert89.core.DiffExporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.sql.DriverManager

@Testcontainers
class PostgresSchemaComparatorTest {
    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:15")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
    }

    @BeforeEach
    fun cleanDatabase() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.use { connection ->
            val statement = connection.createStatement()
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
            // Drop and recreate public schema to clean all objects
            statement.execute("DROP SCHEMA IF EXISTS public CASCADE")
            statement.execute("CREATE SCHEMA public")
            statement.execute("GRANT ALL ON SCHEMA public TO ${postgres.username}")
        }
    }

    @Test
    fun `fetch schema should return empty when no objects`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        // Filter out global objects (roles, tablespaces, etc.) to check schema-specific objects
        val excludedTypes =
            listOf(
                "ROLE", "TABLESPACE", "PUBLICATION", "SUBSCRIPTION",
                "SCHEMA", "EXTENSION", "AGGREGATE", "OPERATOR", "CAST", "FTS_CONFIGURATION",
            )
        val schemaObjects = schema.objects.filter { it.type !in excludedTypes }
        assertTrue(
            schemaObjects.isEmpty(),
            "Expected no schema objects, but found: ${schema.objects.map { "${it.type}:${it.name}" }}",
        )
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

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
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
    fun `fetch schema should include view with columns`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE users (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100)
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE VIEW active_users AS
            SELECT id, name FROM users WHERE name IS NOT NULL
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val views = schema.objects.filter { it.type == "VIEW" }
        assertEquals(1, views.size)
        val view = views.first() as PostgresView
        assertEquals("public.active_users", view.name)
        assertEquals(2, view.columns.size)
        assertNotNull(view.definition)

        val idColumn = view.columns.find { it.columnName == "id" }
        assertNotNull(idColumn)

        connection.close()
    }

    @Test
    fun `fetch schema should include function`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE FUNCTION get_current_timestamp()
            RETURNS TIMESTAMP AS $$
            BEGIN
                RETURN NOW();
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val functions = schema.objects.filter { it.type == "FUNCTION" }
        assertEquals(1, functions.size)
        val function = functions.first() as PostgresFunction
        assertEquals("public.get_current_timestamp()", function.name)
        assertEquals("timestamp without time zone", function.returnType)
        assertEquals("plpgsql", function.language)
        assertEquals("", function.identityArguments)
        assertTrue(function.definition.contains("CREATE OR REPLACE FUNCTION"))

        connection.close()
    }

    @Test
    fun `fetch schema should include procedure`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE IF NOT EXISTS audit_log (
                id SERIAL PRIMARY KEY,
                action VARCHAR(100),
                created_at TIMESTAMP DEFAULT NOW()
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE PROCEDURE log_action(action_text VARCHAR)
            LANGUAGE SQL
            AS $$
                INSERT INTO audit_log (action) VALUES (action_text);
            $$;
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val procedures = schema.objects.filter { it.type == "PROCEDURE" }
        assertEquals(1, procedures.size)
        val procedure = procedures.first() as PostgresProcedure
        assertEquals("public.log_action(IN action_text character varying)", procedure.name)
        assertEquals("sql", procedure.language)
        assertEquals("IN action_text character varying", procedure.identityArguments)
        assertTrue(procedure.definition.contains("CREATE OR REPLACE PROCEDURE"))

        connection.close()
    }

    @Test
    fun `fetch schema should include sequence`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE SEQUENCE custom_seq
                START WITH 100
                INCREMENT BY 10
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val sequences = schema.objects.filter { it.type == "SEQUENCE" }
        assertEquals(1, sequences.size)
        val sequence = sequences.first() as PostgresSequence
        assertEquals("public.custom_seq", sequence.name)
        assertEquals(100L, sequence.startValue)
        assertEquals(10L, sequence.increment)

        connection.close()
    }

    @Test
    fun `fetch schema should include domain`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE DOMAIN positive_integer AS INTEGER
                CHECK (VALUE > 0)
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val domains = schema.objects.filter { it.type == "DOMAIN" }
        assertEquals(1, domains.size)
        val domain = domains.first() as PostgresDomain
        assertEquals("public.positive_integer", domain.name)
        assertEquals("integer", domain.baseType)
        assertNotNull(domain.checkConstraint)

        connection.close()
    }

    @Test
    fun `fetch schema should include extension`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE EXTENSION IF NOT EXISTS "uuid-ossp"
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val extensions = schema.objects.filter { it.type == "EXTENSION" }
        assertTrue(extensions.isNotEmpty())
        val uuidExtension = extensions.find { it.name.contains("uuid-ossp") }
        assertNotNull(uuidExtension)

        connection.close()
    }

    @Test
    fun `fetch schema should include trigger`() {
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
                name VARCHAR(100),
                created_at TIMESTAMP
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE FUNCTION set_created_at()
            RETURNS TRIGGER AS $$
            BEGIN
                NEW.created_at = NOW();
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE TRIGGER trg_before_insert
            BEFORE INSERT ON test_table
            FOR EACH ROW
            EXECUTE FUNCTION set_created_at()
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val triggers = schema.objects.filter { it.type == "TRIGGER" }
        assertEquals(1, triggers.size)
        val trigger = triggers.first() as PostgresTrigger
        assertEquals("test_table", trigger.tableName)
        assertEquals("BEFORE", trigger.timing)
        assertEquals(setOf("INSERT"), trigger.events)
        assertEquals("public.set_created_at", trigger.function)

        connection.close()
    }

    @Test
    fun `fetch schema should include materialized view`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE orders (
                id SERIAL PRIMARY KEY,
                total NUMERIC(10,2)
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE MATERIALIZED VIEW order_summary AS
            SELECT id, total FROM orders
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val matViews = schema.objects.filter { it.type == "MATERIALIZED_VIEW" }
        assertEquals(1, matViews.size, "Expected 1 materialized view, found: ${matViews.map { it.name }}")
        val matView = matViews.first() as PostgresMaterializedView
        assertEquals("public.order_summary", matView.name)
        assertEquals(2, matView.columns.size, "Expected 2 columns, found: ${matView.columns.map { it.columnName }}")
        assertNotNull(matView.definition)

        connection.close()
    }

    @Test
    fun `fetch schema should include policy`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE sensitive_data (
                id SERIAL PRIMARY KEY,
                data TEXT
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            ALTER TABLE sensitive_data ENABLE ROW LEVEL SECURITY
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE POLICY all_access ON sensitive_data
            FOR ALL
            TO PUBLIC
            USING (true)
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val policies = schema.objects.filter { it.type == "POLICY" }
        assertEquals(1, policies.size)
        val policy = policies.first() as PostgresPolicy
        assertEquals("sensitive_data", policy.tableName)
        assertEquals("ALL", policy.command)

        connection.close()
    }

    @Test
    fun `fetch schema should include comment`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE commented_table (
                id SERIAL PRIMARY KEY
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            COMMENT ON TABLE commented_table IS 'This is a test table'
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val comments = schema.objects.filter { it.type == "COMMENT" }
        assertEquals(1, comments.size)
        val comment = comments.first() as PostgresComment
        assertEquals("TABLE", comment.objectType)
        assertEquals("commented_table", comment.commentedObjectName)
        assertEquals("This is a test table", comment.comment)

        connection.close()
    }

    @Test
    fun `fetch schema should include enum type`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TYPE order_status AS ENUM ('pending', 'processing', 'completed')
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val enums = schema.objects.filter { it.type == "ENUM" }
        assertEquals(1, enums.size)
        val enumType = enums.first() as PostgresEnumType
        assertEquals("public.order_status", enumType.name)
        assertEquals(listOf("pending", "processing", "completed"), enumType.values)

        connection.close()
    }

    @Test
    fun `fetch schema should include index`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE indexed_table (
                id SERIAL PRIMARY KEY,
                email VARCHAR(100)
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE UNIQUE INDEX idx_email ON indexed_table(email)
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val tables = schema.objects.filter { it.type == "TABLE" }
        assertEquals(1, tables.size)
        val table = tables.first() as PostgresTable

        val emailIndex = table.indexes.find { it.indexName == "idx_email" }
        assertNotNull(emailIndex)
        assertTrue(emailIndex?.isUnique ?: false)
        assertEquals("btree", emailIndex?.accessMethod)

        connection.close()
    }

    @Test
    fun `fetch schema should include constraint`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE constrained_table (
                id SERIAL PRIMARY KEY,
                email VARCHAR(100) UNIQUE,
                status VARCHAR(20) DEFAULT 'active'
            )
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val tables = schema.objects.filter { it.type == "TABLE" }
        assertEquals(1, tables.size)
        val table = tables.first() as PostgresTable

        val pkConstraint = table.constraints.find { it.constraintType == "PRIMARY KEY" }
        assertNotNull(pkConstraint)

        val uniqueConstraint = table.constraints.find { it.constraintType == "UNIQUE" }
        assertNotNull(uniqueConstraint)

        connection.close()
    }

    @Test
    fun `fetch schema should preserve overloaded function identities and diff`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE FUNCTION overloaded_fn(value_text TEXT)
            RETURNS TEXT AS $$
            BEGIN
                RETURN value_text;
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE FUNCTION overloaded_fn(value_int INTEGER)
            RETURNS INTEGER AS $$
            BEGIN
                RETURN value_int;
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        )

        val source = PostgresSchemaFetcher.fetchSchema(connection)
        val functionNames = source.objects.filterIsInstance<PostgresFunction>().map { it.name }.sorted()
        assertEquals(
            listOf(
                "public.overloaded_fn(value_int integer)",
                "public.overloaded_fn(value_text text)",
            ),
            functionNames,
        )

        connection.createStatement().execute(
            """
            CREATE OR REPLACE FUNCTION overloaded_fn(value_text TEXT)
            RETURNS TEXT AS $$
            BEGIN
                RETURN upper(value_text);
            END;
            $$ LANGUAGE plpgsql
            """.trimIndent(),
        )
        val target = PostgresSchemaFetcher.fetchSchema(connection)

        val diff = PostgresSchemaComparator().compare(source, target)
        assertEquals(1, diff.modified.size)
        assertEquals("public.overloaded_fn(value_text text)", diff.modified.first().first.name)

        connection.close()
    }

    @Test
    fun `fetch schema should preserve overloaded procedure identities`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute("CREATE TABLE audit_log (id SERIAL PRIMARY KEY, action TEXT)")
        connection.createStatement().execute(
            """
            CREATE OR REPLACE PROCEDURE overloaded_proc(action_text TEXT)
            LANGUAGE SQL
            AS $$ INSERT INTO audit_log(action) VALUES (action_text) $$;
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE OR REPLACE PROCEDURE overloaded_proc(action_id INTEGER)
            LANGUAGE SQL
            AS $$ INSERT INTO audit_log(action) VALUES (action_id::text) $$;
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val procedures = schema.objects.filterIsInstance<PostgresProcedure>().map { it.name }.sorted()
        assertEquals(
            listOf(
                "public.overloaded_proc(IN action_id integer)",
                "public.overloaded_proc(IN action_text text)",
            ),
            procedures,
        )

        connection.close()
    }

    @Test
    fun `fetch schema should include rich constraint and index metadata`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute(
            """
            CREATE TABLE parent_table (
                id INTEGER PRIMARY KEY
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE TABLE rich_table (
                id SERIAL PRIMARY KEY,
                parent_id INTEGER REFERENCES parent_table(id),
                email TEXT NOT NULL,
                deleted_at TIMESTAMP,
                CONSTRAINT chk_email CHECK (position('@' in email) > 1)
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE INDEX idx_rich_email_active
            ON rich_table(email)
            WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE INDEX idx_rich_email_lower
            ON rich_table ((lower(email)))
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val table = schema.objects.filterIsInstance<PostgresTable>().single { it.pgObjectName == "rich_table" }

        val foreignKey = table.constraints.single { it.constraintType == "FOREIGN KEY" }
        assertEquals(listOf("parent_id"), foreignKey.columns)
        assertEquals("public.parent_table", foreignKey.referencedTable)
        assertEquals(listOf("id"), foreignKey.referencedColumns)
        assertTrue(foreignKey.definition.contains("REFERENCES parent_table(id)"))

        val check = table.constraints.single { it.constraintType == "CHECK" }
        assertNotNull(check.checkClause)
        assertTrue(check.checkClause.orEmpty().contains("CHECK"))

        val partialIndex = table.indexes.single { it.indexName == "idx_rich_email_active" }
        assertTrue(partialIndex.predicate.orEmpty().contains("deleted_at"))
        assertFalse(partialIndex.isExpressionBased)

        val expressionIndex = table.indexes.single { it.indexName == "idx_rich_email_lower" }
        assertTrue(expressionIndex.isExpressionBased)
        assertTrue(expressionIndex.indexDefinition.contains("lower(email)"))

        connection.close()
    }

    @Test
    fun `fetch schema should scope extensions and include grants`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute("CREATE SCHEMA ext_schema")
        connection.createStatement().execute("CREATE EXTENSION IF NOT EXISTS hstore WITH SCHEMA ext_schema")
        connection.createStatement().execute("CREATE TABLE granted_table (id INT)")
        connection.createStatement().execute("GRANT SELECT ON granted_table TO PUBLIC")

        val scopedSchema = PostgresSchemaFetcher.fetchSchema(connection, "public")
        assertTrue(scopedSchema.objects.filterIsInstance<PostgresExtension>().none { it.schema != "public" })

        val fullSchema = PostgresSchemaFetcher.fetchSchema(connection)
        val grants = fullSchema.objects.filterIsInstance<PostgresGrant>()
        assertTrue(grants.any { it.targetObjectName == "granted_table" && it.privilege == "SELECT" })

        connection.close()
    }

    @Test
    fun `fetch schema should include publications subscriptions and fts without leaking secrets`() {
        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute("CREATE TABLE replicated_table (id INT PRIMARY KEY, body TEXT)")
        connection.createStatement().execute("CREATE PUBLICATION pub_all FOR TABLE replicated_table")
        connection.createStatement().execute(
            """
            CREATE SUBSCRIPTION sub_masked
            CONNECTION 'host=127.0.0.1 port=5432 dbname=testdb user=repl password=secretpass'
            PUBLICATION pub_all
            WITH (connect = false)
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE TEXT SEARCH CONFIGURATION public.test_config ( COPY = pg_catalog.simple )
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)

        val publication = schema.objects.filterIsInstance<PostgresPublication>().single { it.pgObjectName == "pub_all" }
        assertEquals(setOf("insert", "update", "delete", "truncate"), publication.publish)
        assertEquals(listOf("public.replicated_table"), publication.tables)

        val subscription = schema.objects.filterIsInstance<PostgresSubscription>().singleOrNull { it.pgObjectName == "sub_masked" }
        if (subscription != null) {
            assertFalse(subscription.connection.contains("secretpass"))
        }

        val fts = schema.objects.filterIsInstance<PostgresFTSConfiguration>().single { it.pgObjectName == "test_config" }
        assertTrue(fts.dictionaries.isNotEmpty())
        assertTrue(fts.dictionaries.values.flatten().isNotEmpty())

        connection.close()
    }

    @Test
    fun `fetch schema should include rules foreign tables partitions schemas roles tablespaces aggregates operators and casts`() {
        postgres.execInContainer(
            "bash",
            "-lc",
            "rm -rf /tmp/driftlocator_ts && install -d -o postgres -g postgres -m 700 /tmp/driftlocator_ts",
        )

        val connection =
            DriverManager.getConnection(
                postgres.jdbcUrl,
                postgres.username,
                postgres.password,
            )
        connection.createStatement().execute("CREATE ROLE app_reader LOGIN")
        connection.createStatement().execute("CREATE TABLESPACE driftlocator_ts LOCATION '/tmp/driftlocator_ts'")
        connection.createStatement().execute(
            """
            CREATE TABLE rule_target (id INT PRIMARY KEY);
            CREATE TABLE rule_audit (id INT PRIMARY KEY);
            CREATE RULE log_insert AS
                ON INSERT TO rule_target
                DO ALSO INSERT INTO rule_audit(id) VALUES (NEW.id);
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE TABLE sales (
                id INT NOT NULL,
                sale_date DATE NOT NULL
            ) PARTITION BY RANGE (sale_date)
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE TABLE sales_2024 PARTITION OF sales
            FOR VALUES FROM ('2024-01-01') TO ('2025-01-01')
            """.trimIndent(),
        )
        connection.createStatement().execute("CREATE EXTENSION postgres_fdw")
        connection.createStatement().execute(
            """
            CREATE SERVER loopback_server
            FOREIGN DATA WRAPPER postgres_fdw
            OPTIONS (host '127.0.0.1', dbname 'testdb', port '5432')
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE FOREIGN TABLE remote_customers (
                id INTEGER,
                email TEXT
            )
            SERVER loopback_server
            OPTIONS (table_name 'customers')
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE AGGREGATE public.sum_plus_one(INTEGER) (
                SFUNC = int4pl,
                STYPE = INTEGER,
                INITCOND = '1'
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE FUNCTION public.concat_bang(TEXT, TEXT)
            RETURNS TEXT AS $$
            BEGIN
                RETURN $1 || '!' || $2;
            END;
            $$ LANGUAGE plpgsql IMMUTABLE
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE OPERATOR public.!! (
                LEFTARG = TEXT,
                RIGHTARG = TEXT,
                PROCEDURE = public.concat_bang
            )
            """.trimIndent(),
        )
        connection.createStatement().execute(
            """
            CREATE DOMAIN source_text AS TEXT;
            CREATE DOMAIN target_text AS TEXT;
            CREATE FUNCTION public.source_to_target(source_text)
            RETURNS target_text AS $$
            BEGIN
                RETURN $1::text::target_text;
            END;
            $$ LANGUAGE plpgsql IMMUTABLE;
            CREATE CAST (source_text AS target_text)
                WITH FUNCTION public.source_to_target(source_text)
                AS ASSIGNMENT;
            """.trimIndent(),
        )

        val schema = PostgresSchemaFetcher.fetchSchema(connection)

        val rule = schema.objects.filterIsInstance<PostgresRule>().single { it.pgObjectName == "log_insert" }
        assertEquals("rule_target", rule.tableName)
        assertEquals("INSERT", rule.event)

        val foreignTable = schema.objects.filterIsInstance<PostgresForeignTable>().single { it.pgObjectName == "remote_customers" }
        assertEquals("loopback_server", foreignTable.serverName)
        assertEquals(listOf("id", "email"), foreignTable.columns.map { it.columnName })

        val partition = schema.objects.filterIsInstance<PostgresPartition>().single { it.pgObjectName == "sales_2024" }
        assertEquals("public.sales", partition.parentTable)
        assertTrue(partition.partitionKey.contains("RANGE"))
        assertTrue(partition.partitionBound.contains("2025-01-01"))

        assertTrue(schema.objects.filterIsInstance<PostgresSchemaObject>().any { it.schemaName == "public" })

        val role = schema.objects.filterIsInstance<PostgresRole>().single { it.pgObjectName == "app_reader" }
        assertTrue(role.canLogin)

        val tablespace = schema.objects.filterIsInstance<PostgresTablespace>().single { it.pgObjectName == "driftlocator_ts" }
        assertTrue(tablespace.location.contains("/tmp/driftlocator_ts"))

        val aggregate = schema.objects.filterIsInstance<PostgresAggregate>().single { it.pgObjectName == "sum_plus_one" }
        assertEquals("int4", aggregate.stateType)
        assertEquals("int4pl", aggregate.sfunc)

        val operator = schema.objects.filterIsInstance<PostgresOperator>().single { it.pgObjectName == "!!" }
        assertEquals("concat_bang", operator.function)
        assertEquals("text", operator.leftType)
        assertEquals("text", operator.rightType)

        val cast = schema.objects.filterIsInstance<PostgresCast>().single { it.sourceType == "source_text" && it.targetType == "target_text" }
        assertEquals("source_to_target", cast.function)
        assertEquals("ASSIGNMENT", cast.context)

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

        val schema = PostgresSchemaFetcher.fetchSchema(connection)
        val comparator = PostgresSchemaComparator()
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
        val source = PostgresSchemaFetcher.fetchSchema(connection)

        // Create a table
        connection.createStatement().execute(
            """
            CREATE TABLE added_table (id INT)
            """.trimIndent(),
        )
        val target = PostgresSchemaFetcher.fetchSchema(connection)

        val comparator = PostgresSchemaComparator()
        val diff = comparator.compare(source, target)
        assertTrue(diff.added.any { it.name == "public.added_table" })
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
        val source = PostgresSchemaFetcher.fetchSchema(connection)

        // Drop table
        connection.createStatement().execute("DROP TABLE removed_table")
        val target = PostgresSchemaFetcher.fetchSchema(connection)

        val comparator = PostgresSchemaComparator()
        val diff = comparator.compare(source, target)
        assertTrue(diff.removed.any { it.name == "public.removed_table" })
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
        val source = PostgresSchemaFetcher.fetchSchema(connection)

        // Add a column
        connection.createStatement().execute(
            """
            ALTER TABLE modified_table ADD COLUMN name VARCHAR(100)
            """.trimIndent(),
        )
        val target = PostgresSchemaFetcher.fetchSchema(connection)

        val comparator = PostgresSchemaComparator()
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
        val source = PostgresSchemaFetcher.fetchSchema(connection)
        // Drop table1 to make it removed in target
        connection.createStatement().execute("DROP TABLE table1")
        connection.createStatement().execute("CREATE TABLE table2 (name VARCHAR(100))")
        val target = PostgresSchemaFetcher.fetchSchema(connection)

        val comparator = PostgresSchemaComparator()
        val diff = comparator.compare(source, target)

        // Export to temporary file
        val tempFile = Files.createTempFile("diff", ".txt")
        DiffExporter.exportToFile(diff, tempFile)

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
