package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourcePopupActionMigrationTest {

    @Test
    fun `source related adapter menus use custom vertical popup action`() {
        listOf(
            "src/main/java/io/legado/app/ui/book/changesource/ChangeBookSourceAdapter.kt",
            "src/main/java/io/legado/app/ui/book/changesource/ChangeChapterSourceAdapter.kt",
            "src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt"
        ).forEach { path ->
            val kt = readProjectFile(path)
            assertFalse("$path should not instantiate AppCompat PopupMenu", kt.contains("PopupMenu(context, view)"))
            assertContains(path, kt, "PopupAction(context).apply")
            assertContains(path, kt, "setVertical(true)")
            assertContains(path, kt, "setDangerValues(")
            assertContains(path, kt, "showAsDropDown(view, 0, 4.dpToPx())")
        }
    }

    @Test
    fun `source popup actions keep source-specific dynamic visibility`() {
        val bookSourceAdapter = readProjectFile(
            "src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt"
        )

        assertContains("BookSourceAdapter.kt", bookSourceAdapter, "if (callBack.sort == BookSourceSort.Default)")
        assertContains("BookSourceAdapter.kt", bookSourceAdapter, "if (source.hasLoginUrl)")
        assertContains("BookSourceAdapter.kt", bookSourceAdapter, "if (source.hasExploreUrl)")
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
