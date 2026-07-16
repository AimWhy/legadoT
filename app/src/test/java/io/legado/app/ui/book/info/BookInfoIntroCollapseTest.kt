package io.legado.app.ui.book.info

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookInfoIntroCollapseTest {

    @Test
    fun `metadata precedes a four line introduction in both orientations`() {
        val portrait = readProjectFile("src/main/res/layout/item_book_info_header.xml")
        val landscape = readProjectFile("src/main/res/layout-land/activity_book_info.xml")

        listOf(portrait, landscape).forEach { xml ->
            val metadata = xml.indexOf("@layout/view_book_info_manage_rows")
            val divider = xml.indexOf("@+id/v_intro_divider")
            val intro = xml.indexOf("@+id/tv_intro")
            val action = xml.indexOf("@+id/tv_intro_expand")
            val actionElement = xml.substring(xml.lastIndexOf('<', action), xml.indexOf("/>", action))

            assertTrue("metadata must be before the intro divider", metadata in 0..<divider)
            assertTrue("divider must be before introduction", divider in 0..<intro)
            assertTrue("introduction must be before its action", intro in 0..<action)
            assertTrue("intro action must not declare a background", !actionElement.contains("android:background="))
            assertTrue(xml.contains("android:maxLines=\"4\""))
            assertTrue(xml.contains("android:ellipsize=\"end\""))
            assertTrue(actionElement.contains("android:visibility=\"gone\""))
        }

        val defaultStrings = readProjectFile("src/main/res/values/strings.xml")
        val zhStrings = readProjectFile("src/main/res/values-zh/strings.xml")
        assertTrue(defaultStrings.contains("name=\"book_intro_expand\">Expand</string>"))
        assertTrue(defaultStrings.contains("name=\"book_intro_collapse\">Collapse</string>"))
        assertTrue(zhStrings.contains("name=\"book_intro_expand\">展开</string>"))
        assertTrue(zhStrings.contains("name=\"book_intro_collapse\">收起</string>"))
    }

    @Test
    fun `activity owns transient intro expansion and checks rendered overflow`() {
        val source = readProjectFile(
            "src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt"
        )

        assertTrue(source.contains("private const val INTRO_COLLAPSED_LINES = 4"))
        assertTrue(source.contains("private var introExpanded = false"))
        assertTrue(source.contains("tvIntro.maxLines = if (introExpanded)"))
        assertTrue(source.contains("TextUtils.TruncateAt.END"))
        assertTrue(source.contains("getEllipsisCount(lastLine)"))
        assertTrue(source.contains("tvIntro.lineCount > INTRO_COLLAPSED_LINES"))
        assertTrue(source.contains("R.string.book_intro_expand"))
        assertTrue(source.contains("R.string.book_intro_collapse"))
        assertTrue(source.contains("bindIntroToggle"))
        // 可见性由 tv_intro 的持久布局监听驱动,每次布局后重算;
        // 一次性 doOnLayout 会与 header 异步装配/多次数据发射赛跑,首载可能永久漏判
        assertTrue(source.contains("tvIntro.addOnLayoutChangeListener"))
        assertTrue(source.contains("upIntroExpandVisibility"))
        assertTrue(!source.contains("tvIntro.doOnLayout"))
        // 布局回调内必须 post 出去再改可见性:同步翻转兄弟视图的重排请求会被
        // 父容器 layout() 收尾清旗吞掉,按钮成 0 尺寸幽灵(模拟器实测)
        assertTrue(source.contains("tvIntro.post { upIntroExpandVisibility"))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.first { it.isFile }.readText()
    }
}
