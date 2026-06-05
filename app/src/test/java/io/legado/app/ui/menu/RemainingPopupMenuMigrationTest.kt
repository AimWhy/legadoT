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

    @Test
    fun `toolbar overflow menus are routed through md3 popup action bridge`() {
        val bridge = readProjectFile("src/main/java/io/legado/app/utils/ToolbarOverflowMenuExtensions.kt")
        val baseActivity = readProjectFile("src/main/java/io/legado/app/base/BaseActivity.kt")
        val baseFragment = readProjectFile("src/main/java/io/legado/app/base/BaseFragment.kt")

        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "fun Toolbar.installMd3OverflowMenu")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "onPrepareMenu: (Menu) -> Unit = {}")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "onPrepareMenu(menu)")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "PopupAction(context).apply")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "setActionItems(")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "showAsDropDown(anchor, 0, 4.dpToPx())")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "item.subMenu == null")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "item.actionView == null")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "requestsActionButton()")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "!item.isActionButton")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "showOverflowMenu()")
        assertContains("BaseActivity.kt", baseActivity, "installMd3OverflowMenu")
        assertContains("BaseActivity.kt", baseActivity, "onPrepareOptionsMenu(menu)")
        assertContains("BaseActivity.kt", baseActivity, "onMenuOpened(Window.FEATURE_OPTIONS_PANEL, menu)")
        assertContains("BaseActivity.kt", baseActivity, "onCompatOptionsItemSelected(menuItem)")
        assertContains("BaseFragment.kt", baseFragment, "installMd3OverflowMenu")
        assertContains("BaseFragment.kt", baseFragment, "onCompatOptionsItemSelected(menuItem)")
    }

    @Test
    fun `popup action rows support optional leading icons for menu item migration`() {
        val popupAction = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupAction.kt")
        val rowXml = readProjectFile("src/main/res/layout/item_popup_action.xml")

        assertContains("PopupAction.kt", popupAction, "data class PopupActionItem")
        assertContains("PopupAction.kt", popupAction, "val icon: Drawable? = null")
        assertContains("PopupAction.kt", popupAction, "fun setActionItems(items: List<PopupActionItem>)")
        assertContains("PopupAction.kt", popupAction, "ivIcon")
        assertContains("PopupAction.kt", popupAction, "root.minimumHeight = 45.dpToPx()")
        assertContains("PopupAction.kt", popupAction, "textView.minHeight = 45.dpToPx()")
        assertFalse("PopupAction vertical rows should not keep 40dp row height", popupAction.contains("minimumHeight = 40.dpToPx()"))
        assertFalse("PopupAction vertical text should not keep 40dp minHeight", popupAction.contains("textView.minHeight = 40.dpToPx()"))
        // Equal-width vertical menu measured from the widest item via a real TextView (so the
        // measurement honours the app's actual typeface and avoids CJK truncation), clamped to a
        // sensible min/max.
        assertContains("PopupAction.kt", popupAction, "measuredWidth")
        assertFalse("PopupAction width must not be measured from a raw Paint (typeface drift)", popupAction.contains("Paint()"))
        assertContains("PopupAction.kt", popupAction, "coerceIn(112.dpToPx(), 280.dpToPx())")
        // Reserve the leading-icon column only when items actually carry icons; checkable rows
        // render their check mark in a trailing column so unchecked rows don't get a leading gap.
        assertContains("PopupAction.kt", popupAction, "reserveIconColumn")
        assertContains("PopupAction.kt", popupAction, "reserveCheckColumn")
        assertContains("PopupAction.kt", popupAction, "items.any { it.icon != null }")
        assertContains("PopupAction.kt", popupAction, "items.any { it.checked }")
        assertFalse("Checkable items must not promote the leading icon column",
            popupAction.contains("items.any { it.icon != null || it.checked }"))
        assertContains("PopupAction.kt", popupAction, "ivCheckEnd")
        assertContains("PopupAction.kt", popupAction, "View.INVISIBLE")
        assertContains("item_popup_action.xml", rowXml, "@+id/iv_check_end")
        // Vertical rows are left-aligned, not centered, and no longer pin a fixed text min width
        assertContains("PopupAction.kt", popupAction, "textView.gravity = Gravity.CENTER_VERTICAL")
        assertFalse("PopupAction vertical rows should not center text", popupAction.contains("if (item.icon == null && !item.checked)"))
        assertFalse("PopupAction should not keep fixed 96dp min width", popupAction.contains("textView.minWidth = 96.dpToPx()"))
        assertFalse("PopupAction should not keep fixed 120dp min width", popupAction.contains("textView.minWidth = 120.dpToPx()"))
        assertContains("PopupAction.kt", popupAction, "selectableItemBackgroundResId")
        assertContains("item_popup_action.xml", rowXml, "@+id/iv_icon")
        assertContains("item_popup_action.xml", rowXml, "?attr/selectableItemBackground")
        assertFalse("item_popup_action.xml should not force vertical row height on horizontal popups", rowXml.contains("android:minHeight=\"48dp\""))
        assertFalse("item_popup_action.xml should not force menu row background on horizontal popups", rowXml.contains("android:background=\"@drawable/selector_menu_item_bg\""))
    }

    @Test
    fun `locally inflated appcompat toolbars get automatic md3 overflow bridge`() {
        val baseActivity = readProjectFile("src/main/java/io/legado/app/base/BaseActivity.kt")
        val bridge = readProjectFile("src/main/java/io/legado/app/utils/ToolbarOverflowMenuExtensions.kt")

        assertContains("BaseActivity.kt", baseActivity, "if (view is Toolbar)")
        assertContains("BaseActivity.kt", baseActivity, "view.installMd3OverflowMenu")
        assertContains("BaseActivity.kt", baseActivity, "view.menu.performIdentifierAction(menuItem.itemId, 0)")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "updateMd3OverflowMenu")
        assertContains("ToolbarOverflowMenuExtensions.kt", bridge, "setOnHierarchyChangeListener")
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
