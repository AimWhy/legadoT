package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemainingPopupMenuMigrationTest {

    @Test
    fun `select action bar renders inflated menus through md3 popup action`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/widget/SelectActionBar.kt")

        assertFalse("SelectActionBar should not show an AppCompat PopupMenu", kt.contains("selMenu?.show()"))
        assertFalse("SelectActionBar should not store an AppCompat PopupMenu instance", kt.contains("private var selMenu: PopupMenu?"))
        assertFalse("SelectActionBar should not instantiate PopupMenu for the overflow UI", kt.contains("PopupMenu(context, binding.ivMenuMore)"))
        assertContains("SelectActionBar.kt", kt, "MenuBuilder(context)")
        assertContains("SelectActionBar.kt", kt, "MenuInflater(context).inflate(resId, this)")
        assertContains("SelectActionBar.kt", kt, "private fun showMoreMenu()")
        assertContains("SelectActionBar.kt", kt, "PopupAction(context).apply")
        assertContains("SelectActionBar.kt", kt, "setVertical(true)")
        assertContains("SelectActionBar.kt", kt, "setItems(")
        assertContains("SelectActionBar.kt", kt, "showAsDropDown(binding.ivMenuMore, 0, 4.dpToPx())")
    }

    @Test
    fun `select action bar keeps menu item listener contract and danger ids`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/widget/SelectActionBar.kt")

        assertContains("SelectActionBar.kt", kt, "fun setOnMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener)")
        assertContains("SelectActionBar.kt", kt, "private var menuItemClickListener: PopupMenu.OnMenuItemClickListener? = null")
        assertContains("SelectActionBar.kt", kt, "item.itemId.toString()")
        assertContains("SelectActionBar.kt", kt, "menuItemClickListener?.onMenuItemClick(menuItem)")
        assertContains("SelectActionBar.kt", kt, "R.id.menu_del_selection")
        assertContains("SelectActionBar.kt", kt, "R.id.menu_del")
        assertContains("SelectActionBar.kt", kt, "setDangerValues(")
    }

    @Test
    fun `file cache and read activity popups use popup action menu builder`() {
        val fileManage = readProjectFile("src/main/java/io/legado/app/ui/file/FileManageActivity.kt")
        val cache = readProjectFile("src/main/java/io/legado/app/ui/book/cache/CacheActivity.kt")
        val readBook = readProjectFile("src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt")

        assertFalse("FileManageActivity should not import platform PopupMenu", fileManage.contains("import android.widget.PopupMenu"))
        assertFalse("FileManageActivity should not instantiate PopupMenu", fileManage.contains("PopupMenu(context, view)"))
        assertContains("FileManageActivity.kt", fileManage, "popupActionMenu(context)")
        assertContains("FileManageActivity.kt", fileManage, "item(context.getString(R.string.delete), \"delete\")")
        assertContains("FileManageActivity.kt", fileManage, "danger(\"delete\")")
        assertContains("FileManageActivity.kt", fileManage, "viewModel.delFile(file)")

        assertFalse("CacheActivity should not import AppCompat PopupMenu", cache.contains("import androidx.appcompat.widget.PopupMenu"))
        assertFalse("CacheActivity should not instantiate PopupMenu", cache.contains("PopupMenu(this, it)"))
        assertFalse("CacheActivity should not implement PopupMenu listener", cache.contains("PopupMenu.OnMenuItemClickListener"))
        assertFalse("CacheActivity should not tint a legacy PopupMenu", cache.contains("applyOpenTint"))
        assertContains("CacheActivity.kt", cache, "showDownloadMenu(it)")
        assertContains("CacheActivity.kt", cache, "popupActionMenu(this)")
        assertContains("CacheActivity.kt", cache, "startDownloadAfterCurrent()")
        assertContains("CacheActivity.kt", cache, "startDownloadAll()")

        assertFalse("ReadBookActivity should not import AppCompat PopupMenu", readBook.contains("import androidx.appcompat.widget.PopupMenu"))
        assertFalse("ReadBookActivity should not instantiate PopupMenu", readBook.contains("PopupMenu(this, it)"))
        assertFalse("ReadBookActivity should not implement PopupMenu listener", readBook.contains("PopupMenu.OnMenuItemClickListener"))
        assertFalse("ReadBookActivity should not tint a legacy PopupMenu", readBook.contains("applyOpenTint"))
        assertContains("ReadBookActivity.kt", readBook, "showChangeSourceMenu(it)")
        assertContains("ReadBookActivity.kt", readBook, "showRefreshMenu(it)")
        assertContains("ReadBookActivity.kt", readBook, "popupActionMenu(this)")
        assertContains("ReadBookActivity.kt", readBook, "showBookChangeSource()")
        assertContains("ReadBookActivity.kt", readBook, "refreshAllChapters()")
    }

    @Test
    fun `unused appcompat popup coordinate helper is removed`() {
        val kt = readProjectFile("src/main/java/io/legado/app/utils/ViewExtensions.kt")

        assertFalse("ViewExtensions should not import MenuPopupHelper after popup migration", kt.contains("MenuPopupHelper"))
        assertFalse("ViewExtensions should not import AppCompat PopupMenu after popup migration", kt.contains("androidx.appcompat.widget.PopupMenu"))
        assertFalse("ViewExtensions should not keep PopupMenu coordinate show extension", kt.contains("fun PopupMenu.show(x: Int, y: Int)"))
        assertFalse("ViewExtensions should not reflect into mPopup", kt.contains("getDeclaredField(\"mPopup\")"))
    }

    @Test
    fun `custom popup surfaces remain outside this migration scope`() {
        mapOf(
            "TextActionMenu.kt" to "src/main/java/io/legado/app/ui/book/read/TextActionMenu.kt",
            "KeyboardToolPop.kt" to "src/main/java/io/legado/app/ui/widget/keyboard/KeyboardToolPop.kt",
            "TimerSliderPopup.kt" to "src/main/java/io/legado/app/ui/book/audio/TimerSliderPopup.kt"
        ).forEach { (name, path) ->
            val kt = readProjectFile(path)

            assertContains(name, kt, "PopupWindow")
            assertFalse("$name should not be migrated to popupActionMenu in this change", kt.contains("popupActionMenu("))
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
