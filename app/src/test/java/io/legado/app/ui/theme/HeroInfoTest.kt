package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N3a 详情页门面哨兵:collapsing 骨架就位+旧形态退役(模糊蒙层/ArcView/横滚 hack) */
class HeroInfoTest {

    /**
     * CollapsingToolbarLayout 构造器无条件 consumeSystemWindowInsets()(1.13.0 字节码实证,
     * 真机验收实锤:子级 applyStatusBarPadding 收到被吞的 insets→头图顶穿状态栏)。
     * 让位必须在消费点之前:root 监听取 statusBars 直接下发 toolBar/llHeader。
     */
    @Test
    fun `status bar clearance reads insets at root before collapsing consumes them`() {
        val src = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue(
            "让位必须走 root 监听",
            src.contains("binding.root.setOnApplyWindowInsetsListenerCompat")
        )
        assertTrue(src.contains("WindowInsetsCompat.Type.statusBars()"))
        assertTrue(
            "CTL 子级挂 applyStatusBarPadding 是死路(insets 已被消费)",
            !src.contains("toolBar.applyStatusBarPadding") && !src.contains("llHeader?.applyStatusBarPadding")
        )
    }

    @Test
    fun `portrait uses collapsing skeleton and retires legacy composition`() {
        val xml = File("src/main/res/layout/activity_book_info.xml").readText()
        assertTrue(xml.contains("CollapsingToolbarLayout"))
        assertTrue(xml.contains("@+id/tool_bar"))
        assertTrue(xml.contains("@+id/tv_toolbar_title"))
        assertTrue("ArcView 必须退役", !xml.contains("ArcView"))
        assertTrue("黑蒙层必须退役", !xml.contains("#50000000"))
        assertTrue("书名横滚 hack 必须退役", !xml.contains("HorizontalScrollView"))
        assertTrue("业务 id 保全(activity 侧:头图+底部操作条)", listOf(
            "@+id/iv_cover", "@+id/tv_name", "@+id/tv_author",
            "@+id/fl_action", "@+id/tv_shelf", "@+id/tv_read", "@+id/refresh_layout",
        ).all { xml.contains(it) })
        // N3a: tv_change_source/tv_change_group/tv_toc_view/tv_intro 随 ll_info 整体迁入
        // item_book_info_header.xml(RecyclerView header),不再留在 activity 布局本身
        val header = File("src/main/res/layout/item_book_info_header.xml").readText()
        assertTrue("业务 id 保全(header 侧:信息卡+简介+目录区头)", listOf(
            "@+id/tv_change_source", "@+id/tv_change_group", "@+id/tv_toc_view", "@+id/tv_intro",
        ).all { header.contains(it) })
    }

    @Test
    fun `base activity overflow install has tool_bar fallback`() {
        val base = File("src/main/java/io/legado/app/base/BaseActivity.kt").readText()
        assertTrue(base.contains("R.id.tool_bar"))
    }

    /**
     * N3b 抽取:氛围管线从 BookInfoActivity body 提为共享扩展 utils/AmbientBackground.kt
     * (N3a 详情头图与 N3b 音频页共用)。行为等价——只是搬家:activity 侧只保留调用点,
     * 管线本体(种子→ambientScheme→35% blend)+eink 守卫移入扩展。
     */
    @Test
    fun `ambient header pipeline wired with eink guard`() {
        val src = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue("N3a 须调共享扩展(管线已抽出)", src.contains("applyAmbientBackground"))
        val ext = File("src/main/java/io/legado/app/utils/AmbientBackground.kt").readText()
        // 取色管线:双色调升级后从 extractSeed 换成 extractPalette(取前两主色),ImageSeedExtractor 仍是源
        assertTrue(ext.contains("ImageSeedExtractor.extractPalette"))
        assertTrue(ext.contains("ambientScheme"))
        assertTrue("氛围是染不是涂:必须有混合", ext.contains("blendARGB"))
        assertTrue("eink 守卫必须在扩展内", ext.contains("isEInkMode"))
    }

    @Test
    fun `land layout is skin-aware sibling with shared ids`() {
        val land = File("src/main/res/layout-land/activity_book_info.xml").readText()
        assertTrue("land 必须接换肤", land.contains("skin_"))
        assertTrue(!land.contains("ArcView") && !land.contains("#50000000"))
        assertTrue(listOf(
            "@+id/iv_cover", "@+id/tv_name", "@+id/tv_author", "@+id/tool_bar",
            "@+id/refresh_layout", "@+id/fl_action", "@+id/tv_intro",
        ).all { land.contains(it) })
    }

    @Test
    fun `arcview is fully retired`() {
        assertTrue(!File("src/main/java/io/legado/app/ui/widget/image/ArcView.kt").exists())
        assertTrue(!File("src/main/res/values/attrs.xml").readText().contains("ArcView"))
    }

    /**
     * N3a 详情页内嵌目录预览区(land 独有):区头(ll_toc)搬到简介之后,其下预览容器,
     * 两布局共享 id;点击预览行复用 readFromChapter 直接定位阅读。
     * portrait 侧已在 toc-listify 批次退役预览容器,改用 RecyclerView 承载完整目录(见下方哨兵)。
     */
    @Test
    fun `toc preview section sits after intro with shared id across layouts`() {
        val land = File("src/main/res/layout-land/activity_book_info.xml").readText()
        assertTrue("land 必须含 ll_toc_preview", land.contains("@+id/ll_toc_preview"))
        assertTrue(
            "预览区必须锁定在简介之后(区位锁定)",
            land.indexOf("@+id/ll_toc_preview") > land.indexOf("@+id/tv_intro")
        )
        val activitySrc =
            File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue("点章直读必须复用 readFromChapter", activitySrc.contains("readFromChapter"))
    }

    /**
     * N3a toc-listify:详情页(portrait)内容区从"NestedScrollView + 手搓 5 章预览"
     * 换成 RecyclerView + ChapterListAdapter 抽取的两个 header 布局,承载完整目录(可倒序)。
     * land 分治不动,仍走 upTocPreview 旧路径(上面的测试覆盖)。
     */
    @Test
    fun `portrait content area is a recyclerview with extracted headers`() {
        val xml = File("src/main/res/layout/activity_book_info.xml").readText()
        assertTrue("内容区应为 RecyclerView", xml.contains("@+id/recycler_view"))
        assertTrue("内容区 NestedScroll 应退役", !xml.contains("NoChildScrollNestedScrollView"))
        val header = File("src/main/res/layout/item_book_info_header.xml").readText()
        assertTrue(header.contains("@+id/tv_intro") && header.contains("@+id/tv_change_source"))
        assertTrue(
            File("src/main/res/layout/item_book_info_toc_header.xml").readText()
                .contains("@+id/iv_toc_sort")
        )
    }

    /**
     * N3a toc-listify:Activity 侧接线哨兵——adapter 装配+header 回调+倒序状态+TocListItem 喂入,
     * 且 land 分治路径(upTocPreview)未被顶替。
     */
    @Test
    fun `activity wires chapter list adapter with headers and reversed order`() {
        val src = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue(src.contains("ChapterListAdapter"))
        assertTrue(src.contains("addHeaderView"))
        assertTrue(src.contains("tocReversed"))
        assertTrue(src.contains("TocListItem.Chapter"))
        assertTrue("点章直读必须复用 readFromChapter", src.contains("readFromChapter"))
        assertTrue("land 分治路径不可被顶替", src.contains("upTocPreview"))
    }
}
