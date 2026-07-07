package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N2 动效层哨兵:MotionTokens 单门约定(eink+系统减少动画)与依赖显式化 */
class MotionLayerTest {

    @Test
    fun `motion tokens exist with dual gate`() {
        val src = File("src/main/java/io/legado/app/help/motion/MotionTokens.kt").readText()
        assertTrue(src.contains("isEInkMode"))
        assertTrue(src.contains("areAnimatorsEnabled()"))
        assertTrue(src.contains("resolveThemeSpringForce"))
    }

    @Test
    fun `dynamicanimation is an explicit dependency`() {
        val toml = File("../gradle/libs.versions.toml").readText()
        assertTrue("直接使用的传递依赖必须显式声明", toml.contains("dynamicanimation"))
    }

    @Test
    fun `press spring wired to read menu action button`() {
        val helper = File("src/main/java/io/legado/app/help/motion/PressSpringEffect.kt").readText()
        assertTrue(helper.contains("MotionTokens.enabled"))
        assertTrue("不得吞事件,保 click 语义", helper.contains("return@setOnTouchListener false") || helper.contains("false // 不消费"))
        val btn = File("src/main/java/io/legado/app/ui/widget/ReadMenuActionButton.kt").readText()
        assertTrue(btn.contains("PressSpringEffect.attach"))
        assertTrue(File("src/main/java/io/legado/app/help/motion/ShapeMorph.kt").exists())
    }

    @Test
    fun `first screen stagger is gated and uses shared anim resources`() {
        assertTrue(File("src/main/res/anim/motion_layout_stagger.xml").exists())
        listOf(
            "src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksFragment.kt",
            "src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt",
            "src/main/java/io/legado/app/ui/book/search/SearchActivity.kt",
        ).forEach { p ->
            val src = File(p).readText()
            assertTrue("$p 应门控接入 stagger", src.contains("motion_layout_stagger") && src.contains("MotionTokens.enabled"))
        }
    }

    @Test
    fun `container transform wired from shelves and search to book info`() {
        val info = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue(info.contains("MaterialContainerTransform"))
        assertTrue(info.contains("book_cover_"))
        listOf(
            "src/main/java/io/legado/app/ui/main/bookshelf/style1/books/BooksFragment.kt",
            "src/main/java/io/legado/app/ui/main/bookshelf/style2/BookshelfFragment2.kt",
            "src/main/java/io/legado/app/ui/book/search/SearchActivity.kt",
        ).forEach { p ->
            assertTrue("$p 应带 ActivityOptions 发射", File(p).readText().contains("makeSceneTransitionAnimation"))
        }
    }

    @Test
    fun `reader launch carries gated fade options`() {
        val info = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue(info.contains("makeCustomAnimation"))
        assertTrue("launch 必须带 options 第二实参", info.contains("options,"))
        assertTrue(!info.contains("readBookResult.launch(intent)"))
    }
}
