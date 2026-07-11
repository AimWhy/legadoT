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
            "@+id/tv_shelf_title", "@+id/tv_shelf_stats", "@+id/continue_reading",
            "@+id/tv_continue_name", "@+id/tv_continue_chapter", "@+id/tv_continue_percent",
        ).all { header.contains(it) })
        // 续读入口是扁平一行(非卡片),不应再残留 Hero 大卡的封面/进度条 id
        assertTrue("Hero 大卡已退役", !header.contains("card_continue") &&
            !header.contains("iv_hero_cover") && !header.contains("pb_hero_progress"))
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

    @Test
    fun `bookshelf2 collapsing header follows dynamic title`() {
        val xml = File("src/main/res/layout/fragment_bookshelf2.xml").readText()
        assertTrue(xml.contains("CollapsingToolbarLayout") && xml.contains("@+id/shelf_header"))
        assertTrue("TitleBar 退役", !xml.contains("TitleBar"))
        val frag = File("src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt").readText()
        assertTrue("动态标题走 setShelfTitle", frag.contains("setShelfTitle") && !frag.contains("titleBar.title"))
    }

    @Test
    fun `items carry read progress except group variants`() {
        assertTrue(File("src/main/res/layout/item_bookshelf_grid.xml").readText().contains("@+id/pb_read_progress"))
        val list = File("src/main/res/layout/item_bookshelf_list.xml").readText()
        assertTrue(list.contains("@+id/pb_read_progress") && list.contains("@+id/tv_read_percent"))
        assertTrue(!File("src/main/res/layout/item_bookshelf_grid_group.xml").readText().contains("pb_read_progress"))
        assertTrue(!File("src/main/res/layout/item_bookshelf_list_group.xml").readText().contains("pb_read_progress"))
    }
}
