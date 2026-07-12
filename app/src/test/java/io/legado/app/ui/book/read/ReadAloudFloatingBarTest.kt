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

    @Test
    fun `float bar layout and host wiring present`() {
        val layout = File("src/main/res/layout/view_read_aloud_float_bar.xml").readText()
        assertTrue("胶囊应有回到朗读位置段", layout.contains("@+id/ll_back_to_speech"))
        assertTrue("胶囊应有从此处朗读段", layout.contains("@+id/ll_read_from_here"))
        val host = File("src/main/res/layout/activity_book_read.xml").readText()
        assertTrue("宿主应 include 胶囊", host.contains("view_read_aloud_float_bar"))
        val act = File("src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt").readText()
        assertTrue("接线:从此处朗读调 readAloud", act.contains("ReadBook.readAloud()"))
        assertTrue("接线:显隐用纯逻辑", act.contains("ReadAloudBarVisibility.shouldShow"))
    }
}
