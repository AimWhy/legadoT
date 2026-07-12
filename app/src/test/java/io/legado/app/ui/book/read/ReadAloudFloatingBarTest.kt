package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N5 Wave D 朗读悬浮胶囊哨兵 */
class ReadAloudFloatingBarTest {
    @Test
    fun `visibility requires running detached and menu hidden`() {
        // 运行中 + 已脱离 + 菜单隐藏 → 显示
        assertTrue(ReadAloudBarVisibility.shouldShow(isRun = true, following = false, menuVisible = false))
        // 跟随中 → 不显示
        assertEquals(false, ReadAloudBarVisibility.shouldShow(true, following = true, menuVisible = false))
        // 未运行 → 不显示
        assertEquals(false, ReadAloudBarVisibility.shouldShow(isRun = false, following = false, menuVisible = false))
        // 菜单可见 → 不显示(避免叠罗汉)
        assertEquals(false, ReadAloudBarVisibility.shouldShow(true, following = false, menuVisible = true))
    }

    @Test
    fun `follow event constant exists`() {
        val bus = File("src/main/java/io/legado/app/constant/EventBus.kt").readText()
        assertTrue("应有 READ_ALOUD_FOLLOW 事件常量", bus.contains("READ_ALOUD_FOLLOW"))
    }
}
