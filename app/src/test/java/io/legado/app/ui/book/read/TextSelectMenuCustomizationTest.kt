package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextSelectMenuCustomizationTest {

    @Test
    fun dialogIsDragReorderableAndPersists() {
        val kt = readProjectFile(
            "src/main/java/io/legado/app/ui/book/read/config/TextSelectMenuConfigDialog.kt"
        )
        assertContains("TextSelectMenuConfigDialog.kt", kt, "ItemTouchCallback(adapter)")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "ItemTouchHelper")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "loadTextSelectMenuConfig(")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "saveTextSelectMenuConfig(")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "R.id.menu_reset_text_select")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "TextSelectMenuConfig.default()")
    }

    @Test
    fun dialogUsesPersistentDividerRows() {
        val kt = readProjectFile(
            "src/main/java/io/legado/app/ui/book/read/config/TextSelectMenuConfigDialog.kt"
        )
        // 分隔行("浮动条"/"更多")是常驻独立的列表行,而非寄生于首个条目
        assertContains("TextSelectMenuConfigDialog.kt", kt, "Row.Divider")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "Row.Entry")
        assertContains("TextSelectMenuConfigDialog.kt", kt, "onClearView")
        // 不再用"上一行归属变化才显示分组头"的脆弱写法(空区时会丢头、拖动时头跟着跑)
        assertMissing("TextSelectMenuConfigDialog.kt", kt, "prev.inBar")
        assertMissing("TextSelectMenuConfigDialog.kt", kt, "MenuConfigItem")
    }

    @Test
    fun textActionMenuIsConfigDriven() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/book/read/TextActionMenu.kt")
        assertContains("TextActionMenu.kt", kt, "private fun buildPartition()")
        assertContains("TextActionMenu.kt", kt, "loadTextSelectMenuConfig(context)")
        assertContains("TextActionMenu.kt", kt, "TextSelectMenuItem.menuIdOf(")
        assertContains("TextActionMenu.kt", kt, "fun onEditTextActionMenu()")
        assertContains("TextActionMenu.kt", kt, "binding.ivMenuMore.setOnLongClickListener")
        assertMissing("TextActionMenu.kt", kt, "subList(0, 5)")
        assertMissing("TextActionMenu.kt", kt, "PreferKey.expandTextMenu")
    }

    @Test
    fun readActivityOpensEditor() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt")
        assertContains("ReadBookActivity.kt", kt, "override fun onEditTextActionMenu()")
        assertContains("ReadBookActivity.kt", kt, "fun showTextSelectMenuConfig()")
        assertContains("ReadBookActivity.kt", kt, "showDialogFragment(TextSelectMenuConfigDialog())")
    }

    @Test
    fun settingsEntryReplacesExpandSwitch() {
        val xml = readProjectFile("src/main/res/xml/pref_config_read.xml")
        assertMissing("pref_config_read.xml", xml, "android:key=\"expandTextMenu\"")
        assertContains("pref_config_read.xml", xml, "android:key=\"customTextMenu\"")
        assertContains("pref_config_read.xml", xml, "@string/text_select_menu_config")

        val more = readProjectFile(
            "src/main/java/io/legado/app/ui/book/read/config/MoreConfigDialog.kt"
        )
        assertContains("MoreConfigDialog.kt", more, "\"customTextMenu\"")
        assertContains("MoreConfigDialog.kt", more, "showTextSelectMenuConfig()")
        assertMissing("MoreConfigDialog.kt", more, "PreferKey.expandTextMenu")
    }

    private fun assertContains(name: String, text: String, expected: String) {
        assertTrue("$name should contain $expected", text.contains(expected))
    }

    @Suppress("unused")
    private fun assertMissing(name: String, text: String, unexpected: String) {
        assertFalse("$name should not contain $unexpected", text.contains(unexpected))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
