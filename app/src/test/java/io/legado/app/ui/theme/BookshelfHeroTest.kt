package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N3c 主书架门面哨兵 */
class BookshelfHeroTest {

    @Test
    fun `shared header carries hero card and stats`() {
        val header = File("src/main/res/layout/view_bookshelf_header.xml").readText()
        assertTrue(listOf(
            "@+id/tv_shelf_title", "@+id/tv_shelf_stats", "@+id/card_continue",
            "@+id/iv_hero_cover", "@+id/tv_hero_name", "@+id/tv_hero_chapter",
            "@+id/pb_hero_progress", "@+id/tv_hero_percent",
        ).all { header.contains(it) })
        val ext = File("src/main/java/io/legado/app/help/book/BookExtensions.kt").readText()
        assertTrue("readProgress 扩展", ext.contains("fun Book.readProgress"))
    }

    @Test
    fun `bookshelf1 collapsing header with independent tabs`() {
        val xml = File("src/main/res/layout/fragment_bookshelf1.xml").readText()
        assertTrue(xml.contains("CollapsingToolbarLayout") && xml.contains("@+id/shelf_header"))
        assertTrue("TitleBar 退役", !xml.contains("TitleBar"))
        assertTrue("tabs 独立成行", xml.contains("@+id/tab_layout") && !xml.contains("view_tab_layout_min"))
        val base = File("src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt").readText()
        assertTrue("头部逻辑一处实现", base.contains("fun bindShelfHeader") && base.contains("fun refreshShelfHeader"))
    }
}
