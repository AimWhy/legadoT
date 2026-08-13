package io.legado.app.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class CommonsDelegationTest {

    @Test
    fun fileOperationsPreserveProjectContracts() {
        val root = Files.createTempDirectory("legado-file-utils").toFile()
        try {
            val source = FileUtils.createFileIfNotExist(root, "source", "value.txt")
            val target = FileUtils.createFileIfNotExist(root, "target", "value.txt")
            val bytes = "内容".toByteArray()

            assertTrue(FileUtils.writeBytes(source.path, bytes))
            assertArrayEquals(bytes, FileUtils.readBytes(source.path))
            assertTrue(FileUtils.copy(source, target))
            assertArrayEquals(bytes, FileUtils.readBytes(target.path))

            val streamTarget = FileUtils.createFileIfNotExist(root, "stream", "value.txt")
            assertTrue(FileUtils.writeInputStream(streamTarget, ByteArrayInputStream(bytes)))
            assertEquals(String(bytes), FileUtils.readText(streamTarget.path))
        } finally {
            FileUtils.delete(root, true)
        }
    }

    @Test
    fun repeatDelegatesWithExistingBoundarySemantics() {
        assertEquals("ababab", StringUtils.repeat("ab", 3))
        assertEquals("", StringUtils.repeat("ab", 0))
        assertEquals("", StringUtils.repeat("ab", -1))
    }
}
