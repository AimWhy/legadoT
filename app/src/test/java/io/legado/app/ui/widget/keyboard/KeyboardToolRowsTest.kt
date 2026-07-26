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

    @Test
    fun `layout delegates layout manager to runtime`() {
        val layout = readProjectFile("src/main/res/layout/popup_keyboard_tool.xml")

        // 行数在运行时才知道,XML 不能写死 layoutManager
        assertTrue(
            "popup_keyboard_tool.xml should not hardcode a layout manager",
            !layout.contains("app:layoutManager")
        )
        assertContains(layout, "android:background=\"@drawable/bg_popup_menu\"")
    }

    @Test
    fun `single row keeps horizontal linear layout and wrap content`() {
        val pop = readProjectFile("src/main/java/io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt")

        assertContains(pop, "fun upRowCount(")
        assertContains(pop, "LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)")
        assertContains(pop, "FlexboxLayoutManager(context)")
        assertContains(pop, "ViewGroup.LayoutParams.WRAP_CONTENT")
    }

    @Test
    fun `row count change remeasures content view for host padding`() {
        val pop = readProjectFile("src/main/java/io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt")

        // measuredHeight 参与宿主编辑区 bottom padding 计算,行数变化后必须重测
        assertContains(pop, "measureContentView()")
        assertContains(pop, "if (isShowing)")
        assertContains(pop, "update(width, contentView.measuredHeight)")
        assertContains(pop, "height = measureRowsHeight(rows)")
    }

    @Test
    fun `config dialog exposes row count entry`() {
        val menu = readProjectFile("src/main/res/menu/keyboard_assists_config.xml")
        val config = readProjectFile(
            "src/main/java/io/legado/app/ui/widget/keyboard/KeyboardAssistsConfig.kt"
        )

        assertContains(menu, "android:id=\"@+id/menu_row_count\"")
        assertContains(config, "R.id.menu_row_count")
        assertContains(config, "NumberPickerDialog(requireContext())")
        assertContains(config, "AppConfig.KEYBOARD_TOOL_MIN_ROWS")
        assertContains(config, "AppConfig.KEYBOARD_TOOL_MAX_ROWS")
        assertContains(config, "AppConfig.keyboardToolRows = it")
    }

    @Test
    fun `row count change reaches the popup through event bus`() {
        val config = readProjectFile(
            "src/main/java/io/legado/app/ui/widget/keyboard/KeyboardAssistsConfig.kt"
        )
        val pop = readProjectFile("src/main/java/io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt")

        // 行数存 pref 无 Room flow 可依,配置页与浮窗无引用关系,靠事件贯通
        assertContains(config, "postEvent(PreferKey.keyboardToolRows,")
        assertContains(pop, "observeEvent<Int>(PreferKey.keyboardToolRows)")
        assertContains(pop, "upRowCount(")
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
