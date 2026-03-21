package com.github.yamert89.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class DiffExporterTest {
    @TempDir
    lateinit var tempDir: Path

    // Simple mock implementation of DatabaseObject for testing
    data class MockObject(
        override val name: String,
        override val type: String,
        override val children: List<DatabaseObject> = emptyList(),
        override val objectName: String = name,
    ) : DatabaseObject

    @Test
    fun `toText with empty diff`() {
        val diff = SchemaDiff(emptyList(), emptyList(), emptyList())
        val text = DiffExporter.toText(diff)
        assertTrue(text.contains("Added Objects"))
        assertTrue(text.contains("Removed Objects"))
        assertTrue(text.contains("Modified Objects"))
        assertTrue(text.contains("(none)"))
    }

    @Test
    fun `toText with added objects`() {
        val added =
            listOf(
                MockObject("table1", "TABLE"),
                MockObject(
                    "table2",
                    "TABLE",
                    listOf(
                        MockObject("col1", "COLUMN"),
                    ),
                ),
            )
        val diff = SchemaDiff(added, emptyList(), emptyList())
        val text = DiffExporter.toText(diff)
        assertTrue(text.contains("table1"))
        assertTrue(text.contains("table2"))
        assertTrue(text.contains("col1"))
    }

    @Test
    fun `toText with removed objects`() {
        val removed = listOf(MockObject("view1", "VIEW"))
        val diff = SchemaDiff(emptyList(), removed, emptyList())
        val text = DiffExporter.toText(diff)
        assertTrue(text.contains("view1"))
    }

    @Test
    fun `toText with modified objects`() {
        val oldObj = MockObject("func1", "FUNCTION")
        val newObj =
            MockObject(
                "func1",
                "FUNCTION",
                listOf(
                    MockObject("param", "PARAMETER"),
                ),
            )
        val diff = SchemaDiff(emptyList(), emptyList(), listOf(oldObj to newObj))
        val text = DiffExporter.toText(diff)
        assertTrue(text.contains("func1"))
        assertTrue(text.contains("Old version"))
        assertTrue(text.contains("New version"))
        assertTrue(text.contains("param"))
    }

    @Test
    fun `exportToFile creates file`() {
        val diff = SchemaDiff(emptyList(), emptyList(), emptyList())
        val file = tempDir.resolve("diff.txt")
        DiffExporter.exportToFile(diff, file)
        assertTrue(file.toFile().exists())
        val content = file.toFile().readText()
        assertTrue(content.isNotEmpty())
    }

    @Test
    fun `toText with schema exports sorted objects`() {
        val objects =
            listOf(
                MockObject("zebra", "TABLE"),
                MockObject("alpha", "TABLE"),
                MockObject("middle", "VIEW"),
            )
        val schema = DatabaseSchema(objects)
        val text = DiffExporter.toText(schema)

        assertTrue(text.contains("Database Schema"))
        assertTrue(text.contains("Total objects: 3"))
        assertTrue(text.contains("[TABLE] alpha"))
        assertTrue(text.contains("[VIEW] middle"))
        assertTrue(text.contains("[TABLE] zebra"))

        // Check alphabetical ordering
        val alphaIndex = text.indexOf("[TABLE] alpha")
        val middleIndex = text.indexOf("[VIEW] middle")
        val zebraIndex = text.indexOf("[TABLE] zebra")
        assertTrue(alphaIndex < middleIndex, "alpha should come before middle")
        assertTrue(middleIndex < zebraIndex, "middle should come before zebra")
    }

    @Test
    fun `toText with schema includes schema name in header`() {
        val schema = DatabaseSchema(emptyList())
        val text = DiffExporter.toText(schema, "my_schema")

        assertTrue(text.contains("Schema: my_schema"))
    }

    @Test
    fun `toText with schema exports children sorted`() {
        val childObjects =
            listOf(
                MockObject("z_col", "COLUMN"),
                MockObject("a_col", "COLUMN"),
                MockObject("m_col", "COLUMN"),
            )
        val schema = DatabaseSchema(listOf(MockObject("table1", "TABLE", childObjects)))
        val text = DiffExporter.toText(schema)

        val aIndex = text.indexOf("[COLUMN] a_col")
        val mIndex = text.indexOf("[COLUMN] m_col")
        val zIndex = text.indexOf("[COLUMN] z_col")
        assertTrue(aIndex < mIndex, "a_col should come before m_col")
        assertTrue(mIndex < zIndex, "m_col should come before z_col")
    }

    @Test
    fun `toText uses objectName instead of name`() {
        val objWithSchema = MockObject("public.table1", "TABLE", emptyList(), "table1")
        val schema = DatabaseSchema(listOf(objWithSchema))
        val text = DiffExporter.toText(schema)

        assertTrue(text.contains("[TABLE] table1"))
        assertTrue(!text.contains("[TABLE] public.table1"))
    }

    @Test
    fun `exportSchemaToFile creates file`() {
        val schema = DatabaseSchema(emptyList())
        val file = tempDir.resolve("schema.txt")
        DiffExporter.exportToFile(schema, file)
        assertTrue(file.toFile().exists())
        val content = file.toFile().readText()
        assertTrue(content.isNotEmpty())
        assertTrue(content.contains("Database Schema"))
    }
}
