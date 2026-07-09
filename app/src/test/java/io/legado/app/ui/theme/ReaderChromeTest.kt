package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N3d 阅读器 chrome 值收尾哨兵 */
class ReaderChromeTest {

    private val chromeLayouts = listOf(
        "view_read_menu", "dialog_read_aloud", "dialog_read_bg_text",
        "dialog_read_book_style", "dialog_read_padding", "view_search_menu",
    )

    @Test
    fun `chrome family is token-aligned with no hardcoded sizes`() {
        chromeLayouts.forEach { name ->
            val xml = File("src/main/res/layout/$name.xml").readText()
            // 去注释后不得有硬编码 textSize/radius(数字形态)
            val active = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            assertTrue("$name 仍有硬编码 textSize", !active.contains(Regex("textSize=\"\\d+sp\"")))
            assertTrue("$name 仍有硬编码 radius", !active.contains(Regex("app:radius=\"\\d+dp\"")))
        }
    }

    @Test
    fun `search menu and action popup carry press spring`() {
        val search = File("src/main/java/io/legado/app/ui/book/read/SearchMenu.kt").readText()
        assertTrue(search.contains("PressSpringEffect"))
        // popup 消费者:TextActionMenu 消费 popup_action_menu(PopupActionMenuBinding),选项行挂弹簧
        val popup = File("src/main/java/io/legado/app/ui/book/read/TextActionMenu.kt").readText()
        assertTrue(popup.contains("PressSpringEffect"))
    }
}
