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
        override val children: List<DatabaseObject> = emptyList()
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
        val added = listOf(
            MockObject("table1", "TABLE"),
            MockObject("table2", "TABLE", listOf(
                MockObject("col1", "COLUMN")
            ))
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
        val newObj = MockObject("func1", "FUNCTION", listOf(
            MockObject("param", "PARAMETER")
        ))
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
}