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
}
