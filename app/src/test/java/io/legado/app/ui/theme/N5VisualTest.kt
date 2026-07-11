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

    @Test
    fun `holo selector purged from debug and page key`() {
        val pk = File("src/main/res/layout/dialog_page_key.xml").readText()
        val dbg = File("src/main/res/layout/activity_source_debug.xml").readText()
        assertTrue("dialog_page_key 不应再用 Holo selector", !pk.contains("selector_fillet_btn_bg"))
        assertTrue("source_debug 不应再用 Holo selector", !dbg.contains("selector_fillet_btn_bg"))
        assertTrue("应改用 ripple 药丸", pk.contains("bg_fillet_btn"))
    }

    @Test
    fun `rss source edit top matches book source card chip`() {
        val xml = File("src/main/res/layout/activity_rss_source_edit.xml").readText()
        assertTrue("RSS 顶部应卡片化", xml.contains("MaterialCardView"))
        assertTrue("RSS 开关应 Chip 化", xml.contains("Widget.App.Chip"))
        assertTrue("应有折叠头摘要", xml.contains("@+id/tv_top_summary"))
        assertTrue("id 保全:cb_is_enable", xml.contains("@+id/cb_is_enable"))
        assertTrue("id 保全:cb_single_url", xml.contains("@+id/cb_single_url"))
    }

    @Test
    fun `book source summary card floats and import has empty icon`() {
        val edit = File("src/main/res/layout/activity_book_source_edit.xml").readText()
        assertTrue("摘要卡应用 background_card 浮起", edit.contains("cardBackgroundColor=\"@color/background_card\""))
        val dlg = File("src/main/res/layout/dialog_recycler_view.xml").readText()
        assertTrue("导入弹窗空态应有图标", dlg.contains("@+id/iv_empty"))
    }
}
