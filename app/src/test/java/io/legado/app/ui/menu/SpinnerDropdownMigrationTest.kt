package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpinnerDropdownMigrationTest {

    @Test
    fun `legacy spinners are replaced by md3 dropdown text fields`() {
        listOf(
            "activity_book_info_edit.xml",
            "dialog_book_group_edit.xml",
            "dialog_bookshelf_config.xml",
            "dialog_rule_sub_edit.xml",
            "dialog_webdav_server.xml"
        ).forEach { layoutName ->
            val xml = readProjectFile("src/main/res/layout/$layoutName")
            assertFalse("$layoutName should not use AppCompatSpinner", xml.contains("AppCompatSpinner"))
            assertContains(layoutName, xml, "app:endIconMode=\"dropdown_menu\"")
            assertContains(layoutName, xml, "io.legado.app.ui.widget.text.AutoCompleteTextView")
            assertContains(layoutName, xml, "android:inputType=\"none\"")
            assertContains(layoutName, xml, "android:minHeight=\"48dp\"")
        }
    }

    @Test
    fun `autocomplete exposes spinner-like selected item helpers`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/widget/text/AutoCompleteTextView.kt")

        assertContains("AutoCompleteTextView.kt", kt, "fun setFilterValues(vararg value: String)")
        assertContains("AutoCompleteTextView.kt", kt, "val selectedItemPosition: Int")
        assertContains("AutoCompleteTextView.kt", kt, "fun setSelectionByIndex(index: Int)")
    }

    @Test
    fun `converted dropdown call sites use selected item helpers`() {
        listOf(
            "src/main/java/io/legado/app/ui/book/import/remote/ServerConfigDialog.kt",
            "src/main/java/io/legado/app/ui/book/group/GroupEditDialog.kt",
            "src/main/java/io/legado/app/ui/book/info/edit/BookInfoEditActivity.kt",
            "src/main/java/io/legado/app/ui/main/bookshelf/BaseBookshelfFragment.kt",
            "src/main/java/io/legado/app/ui/rss/subscription/RuleSubActivity.kt"
        ).forEach { path ->
            val kt = readProjectFile(path)
            assertContains(path, kt, ".setFilterValues(*resources.getStringArray(")
            assertContains(path, kt, ".setSelectionByIndex(")
        }
    }

    private fun assertContains(name: String, text: String, expected: String) {
        assertTrue("$name should contain $expected", text.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(
            File(pathInApp),
            File("app/$pathInApp")
        )
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
