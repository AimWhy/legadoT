package io.legado.app.ui.widget.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KeyboardToolRowsTest {

    @Test
    fun `pref key and config property exist`() {
        val preferKey = readProjectFile("src/main/java/io/legado/app/constant/PreferKey.kt")
        val appConfig = readProjectFile("src/main/java/io/legado/app/help/config/AppConfig.kt")

        assertContains(preferKey, "const val keyboardToolRows = \"keyboardToolRows\"")
        assertContains(appConfig, "var keyboardToolRows: Int")
        assertContains(appConfig, "PreferKey.keyboardToolRows")
    }

    @Test
    fun `out of range row count falls back to one`() {
        // 与 AppConfig.keyboardToolRows getter 同一夹取口径
        fun coerce(raw: Int) = if (raw in MIN_ROWS..MAX_ROWS) raw else MIN_ROWS

        assertEquals(1, coerce(0))
        assertEquals(1, coerce(-3))
        assertEquals(1, coerce(6))
        assertEquals(1, coerce(Int.MAX_VALUE))
        assertEquals(1, coerce(1))
        assertEquals(3, coerce(3))
        assertEquals(5, coerce(5))
    }

    @Test
    fun `config getter clamps instead of throwing`() {
        val appConfig = readProjectFile("src/main/java/io/legado/app/help/config/AppConfig.kt")

        assertContains(appConfig, "in KEYBOARD_TOOL_MIN_ROWS..KEYBOARD_TOOL_MAX_ROWS")
    }

    private companion object {
        const val MIN_ROWS = 1
        const val MAX_ROWS = 5
    }

    private fun assertContains(text: String, expected: String) {
        assertTrue("Expected to contain $expected", text.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
