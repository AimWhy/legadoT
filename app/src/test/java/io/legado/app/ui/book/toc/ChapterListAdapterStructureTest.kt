package io.legado.app.ui.book.toc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChapterListAdapterStructureTest {

    @Test
    fun `adapter renders TocListItem instead of owning full chapter folding state`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/book/toc/ChapterListAdapter.kt")

        assertTrue(kt.contains("DiffRecyclerAdapter<TocListItem, ItemChapterListBinding>"))
        assertFalse(kt.contains("private var allItems"))
        assertFalse(kt.contains("collapsedVolumes"))
        assertFalse(kt.contains("computeVisibleItems"))
        assertFalse(kt.contains("fun setAllItems"))
        assertTrue(kt.contains("is TocListItem.Volume"))
        assertTrue(kt.contains("is TocListItem.Chapter"))
    }

    @Test
    fun `fragment no longer treats chapter index as adapter position for cache refresh`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/book/toc/ChapterListFragment.kt")

        assertTrue(kt.contains("notifyVisibleChapterChanged(chapter.index)"))
        assertTrue(kt.contains("adapter.findVisiblePositionByChapterIndex(chapterIndex)"))
        assertFalse(kt.contains("adapter.notifyItemChanged(chapter.index, true)"))
    }

    private fun readProjectFile(path: String): String {
        return File(System.getProperty("user.dir"), path).readText()
    }
}
