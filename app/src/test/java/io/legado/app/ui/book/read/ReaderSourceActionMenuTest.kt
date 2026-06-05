package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderSourceActionMenuTest {

    @Test
    fun `reader source action uses pill chip styling`() {
        listOf("view_read_menu.xml", "view_manga_menu.xml").forEach { layoutName ->
            val xml = readProjectFile("src/main/res/layout/$layoutName")
            assertContains(layoutName, xml, "android:id=\"@+id/tv_source_action\"")
            assertContains(layoutName, xml, "android:minHeight=\"32dp\"")
            assertContains(layoutName, xml, "android:paddingStart=\"@dimen/space_m\"")
            assertContains(layoutName, xml, "android:paddingEnd=\"@dimen/space_s\"")
            assertContains(layoutName, xml, "app:drawableEndCompat=\"@drawable/ic_arrow_drop_down\"")
            assertContains(layoutName, xml, "app:drawableTint=\"@color/primaryText\"")
            assertContains(layoutName, xml, "app:radius=\"16dp\"")
            assertFalse("$layoutName should not keep the old 2dp square radius", xml.contains("app:radius=\"2dp\""))
        }
    }

    @Test
    fun `reader source action menu uses custom popup instead of system popup menu`() {
        val readMenuKt = readProjectFile("src/main/java/io/legado/app/ui/book/read/ReadMenu.kt")

        assertFalse(readMenuKt.contains("PopupMenu(context, binding.tvSourceAction)"))
        assertContains("ReadMenu.kt", readMenuKt, "private val sourceActionMenu by lazy")
        assertContains("ReadMenu.kt", readMenuKt, "PopupAction(context)")
        assertContains("ReadMenu.kt", readMenuKt, "setVertical(true)")
        assertContains("ReadMenu.kt", readMenuKt, "setDangerValues(setOf(\"disableSource\"))")
        assertContains("ReadMenu.kt", readMenuKt, "showAsDropDown(binding.tvSourceAction")
    }

    @Test
    fun `popup action supports vertical source menus and danger items`() {
        val popupActionKt = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupAction.kt")

        assertContains("PopupAction.kt", popupActionKt, "fun setVertical(vertical: Boolean)")
        assertContains("PopupAction.kt", popupActionKt, "fun setDangerValues(values: Set<String>)")
        assertContains("PopupAction.kt", popupActionKt, "LinearLayoutManager(context)")
        assertContains("PopupAction.kt", popupActionKt, "FlexboxLayoutManager(context)")
        assertContains("PopupAction.kt", popupActionKt, "context.getCompatColor(R.color.error)")
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
