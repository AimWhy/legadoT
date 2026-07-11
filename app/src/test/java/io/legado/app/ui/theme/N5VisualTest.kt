package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N5 Wave B 视觉收尾哨兵 */
class N5VisualTest {
    @Test
    fun `app slider style tames thumb and gap`() {
        val styles = File("src/main/res/values/styles.xml").readText()
        assertTrue("应定义 Widget.App.Slider", styles.contains("name=\"Widget.App.Slider\""))
        assertTrue("应去缺口 thumbTrackGapSize=0", styles.contains("thumbTrackGapSize"))
        assertTrue("应去尾端停止点 trackStopIndicatorSize", styles.contains("trackStopIndicatorSize"))
        assertTrue("Base.AppTheme 应挂 sliderStyle", styles.contains("name=\"sliderStyle\""))
    }

    @Test
    fun `bookmark item is carded`() {
        val xml = File("src/main/res/layout/item_bookmark.xml").readText()
        assertTrue("书签 item 应套卡片", xml.contains("Style.ItemManageCard"))
        assertTrue("卡容器应声明 surfaceContainerLow", xml.contains("surfaceContainerLow"))
        assertTrue("id 保全:tv_chapter_name", xml.contains("@+id/tv_chapter_name"))
        assertTrue("id 保全:tv_content", xml.contains("@+id/tv_content"))
    }

    @Test
    fun `search content item is carded`() {
        val xml = File("src/main/res/layout/item_search_list.xml").readText()
        assertTrue("书内搜索 item 应套卡片", xml.contains("Style.ItemManageCard"))
        assertTrue("id 保全:tv_search_result", xml.contains("@+id/tv_search_result"))
        assertTrue("修正约束笔误", xml.contains("constraintRight_toRightOf"))
    }

    @Test
    fun `font item selection has stroke highlight`() {
        val xml = File("src/main/res/layout/item_font.xml").readText()
        assertTrue("字体 item root 应为 MaterialCardView 承接描边", xml.contains("MaterialCardView"))
        val adapter = File("src/main/java/io/legado/app/ui/font/FontAdapter.kt").readText()
        assertTrue("选中态应设描边", adapter.contains("strokeWidth") || adapter.contains("setStrokeColor"))
    }
}
