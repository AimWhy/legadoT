package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 12 收敛哨兵：BookSourceEditActivity / AutoTaskEditActivity 的手搭字段导航条
 * 已被共享组件 FieldNavBar 取代，RSS 编辑页首次获得导航条。
 */
class FieldNavMigrationTest {

    @Test
    fun `hand-rolled nav impl is gone and shared component wired`() {
        listOf(
            "src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt",
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskEditActivity.kt",
        ).forEach { p ->
            val src = File(p).readText()
            assertTrue("$p 仍有手搭导航条", !src.contains("highlightNavItem"))
            assertTrue(src.contains("FieldNavBar") || src.contains("fieldNav"))
        }
        assertTrue(!File("src/main/res/drawable/bg_field_nav_item.xml").exists())
        val rss = File("src/main/res/layout/activity_rss_source_edit.xml").readText()
        assertTrue("RSS 编辑应获得导航条", rss.contains("FieldNavBar"))
    }
}
