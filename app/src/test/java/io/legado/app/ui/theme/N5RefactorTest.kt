package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N5 Wave C 重构收敛哨兵(行为等价,验证共享物+去重) */
class N5RefactorTest {
    @Test
    fun `detail seekbar buttons share step helper`() {
        val src = File("src/main/java/io/legado/app/ui/widget/DetailSeekBar.kt").readText()
        assertTrue("应抽出 step 私有方法", src.contains("private fun step("))
        // 两钮都调 step,不再各自内联 coerceIn
        assertTrue("加钮调 step", src.contains("step(1)"))
        assertTrue("减钮调 step", src.contains("step(-1)"))
    }

    @Test
    fun `edit adapter tag1 listener reads current safety not frozen`() {
        listOf(
            "book/source/edit/BookSourceEditAdapter",
            "rss/source/edit/RssSourceEditAdapter",
        ).forEach {
            val src = File("src/main/java/io/legado/app/ui/$it.kt").readText()
            // 修复后:attach 回调内重算而非用外层冻结的 isUnsafeText
            assertTrue("$it 应每次 attach 重读安全态",
                src.contains("EditSafety.isCombiningHeavy") &&
                    src.contains("onViewAttachedToWindow"))
        }
    }

    @Test
    fun `cover transition launch is shared`() {
        val ext = File("src/main/java/io/legado/app/utils/ActivityExtensions.kt").readText()
        assertTrue("应有共享发射扩展", ext.contains("fun Activity.startBookInfoTransition"))
        listOf(
            "ui/main/bookshelf/style1/books/BooksFragment",
            "ui/main/bookshelf/style2/BookshelfFragment2",
            "ui/book/search/SearchActivity",
        ).forEach {
            val src = File("src/main/java/io/legado/app/$it.kt").readText()
            assertTrue("$it 应调共享扩展", src.contains("startBookInfoTransition"))
            assertTrue("$it 不应再内联 makeSceneTransitionAnimation",
                !src.contains("makeSceneTransitionAnimation"))
        }
    }

    @Test
    fun `ambient background returns cancellable job`() {
        val ext = File("src/main/java/io/legado/app/utils/AmbientBackground.kt").readText()
        assertTrue("applyAmbientBackground 应返回 Job", ext.contains("): Job"))
        val info = File("src/main/java/io/legado/app/ui/book/info/BookInfoActivity.kt").readText()
        assertTrue("BookInfoActivity 应持 job 取消", info.contains("ambientJob"))
    }

    @Test
    fun `readrss uses modern insets not flag_fullscreen`() {
        val src = File("src/main/java/io/legado/app/ui/rss/read/ReadRssActivity.kt").readText()
        assertTrue("onConfigurationChanged 不应再用 FLAG_FULLSCREEN 切换",
            !src.contains("FLAG_FULLSCREEN") || src.contains("Type.statusBars()"))
    }

    @Test
    fun `book info manage rows are shared via include`() {
        // C5a:land 与头卡的管理三行块(书源/最新/分组)已抽为共享 include,不再各自逐字重复。
        val landSrc = File("src/main/res/layout-land/activity_book_info.xml").readText()
        val headerSrc = File("src/main/res/layout/item_book_info_header.xml").readText()
        assertTrue("头卡应 include 共享管理行布局",
            headerSrc.contains("layout=\"@layout/view_book_info_manage_rows\""))
        assertTrue("land 应通过共享 RecyclerView 使用头卡", landSrc.contains("@+id/recycler_view"))
        val shared = File("src/main/res/layout/view_book_info_manage_rows.xml").readText()
        // id 全部保留,BookInfoActivity 才能继续 binding 访问
        listOf("tv_origin", "tv_change_source", "tv_lasted", "tv_group", "tv_change_group").forEach {
            assertTrue("共享布局应保留 id=$it", shared.contains("@+id/$it"))
        }
    }
}
