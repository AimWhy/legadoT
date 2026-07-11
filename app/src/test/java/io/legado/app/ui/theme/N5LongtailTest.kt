package io.legado.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** N5 长尾收官 Wave A 清尸哨兵 */
class N5LongtailTest {
    @Test
    fun `vertical seekbar family retired`() {
        assertFalse("VerticalSeekBar.kt 应删除",
            File("src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBar.kt").exists())
        assertFalse("VerticalSeekBarWrapper.kt 应删除",
            File("src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBarWrapper.kt").exists())
        val attrs = File("src/main/res/values/attrs.xml").readText()
        assertFalse("VerticalSeekBar styleable 应删除", attrs.contains("name=\"VerticalSeekBar\""))
    }

    @Test
    fun `orphan layout and dead adapter methods removed`() {
        assertFalse("view_refresh_recycler.xml 孤儿应删除",
            File("src/main/res/layout/view_refresh_recycler.xml").exists())
        val adapter = File("src/main/java/io/legado/app/base/adapter/DiffRecyclerAdapter.kt").readText()
        assertFalse("setItem(position,item) 死方法应删除", adapter.contains("fun setItem(position: Int, item: ITEM)"))
        assertFalse("updateItem(item) 死方法应删除", adapter.contains("fun updateItem(item: ITEM)"))
        org.junit.Assert.assertTrue("updateItems(区间) 有活调用者须保留", adapter.contains("fun updateItems("))
    }

    @Test
    fun `motion tokens annotated and chapter adapter offset fixed`() {
        val motion = File("src/main/java/io/legado/app/help/motion/MotionTokens.kt").readText()
        org.junit.Assert.assertTrue("Spring.attr 应加 @AttrRes", motion.contains("@AttrRes"))
        org.junit.Assert.assertTrue("Spring.defStyle 应加 @StyleRes", motion.contains("@StyleRes"))
        val toc = File("src/main/java/io/legado/app/ui/book/toc/ChapterListAdapter.kt").readText()
        assertFalse("upDisplayTitles 不应裸用 notifyItemChanged(i, true)",
            toc.contains("notifyItemChanged(i, true)"))
    }
}
