package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogEntryTest {

    private fun entry(
        throwable: Throwable? = null,
        tag: String? = null,
        httpId: Long? = null,
        error: Boolean = false,
    ) = AppLog.Entry(
        id = 1, time = 0, message = "msg",
        throwable = throwable, tag = tag, httpId = httpId, error = error,
    )

    @Test
    fun `类别谓词可多中`() {
        val e = entry(tag = "某源", throwable = RuntimeException("x"))
        assertTrue(e.isError)
        assertTrue(e.isSource)
        assertFalse(e.isHttp)
    }

    @Test
    fun `显式error标记无throwable也算错误`() {
        assertTrue(entry(error = true).isError)
        assertFalse(entry().isError)
    }

    @Test
    fun `category单优先级错误最高信息兜底`() {
        assertEquals(
            AppLog.Entry.Category.ERROR,
            entry(httpId = 1, tag = "t", error = true).category
        )
        assertEquals(AppLog.Entry.Category.HTTP, entry(httpId = 1, tag = "t").category)
        assertEquals(AppLog.Entry.Category.SOURCE, entry(tag = "t").category)
        assertEquals(AppLog.Entry.Category.INFO, entry().category)
    }

    @Test
    fun `导出为时间升序且带tag或类别标注`() {
        val newer = AppLog.Entry(id = 2, time = 1000, message = "second", tag = "某源")
        val older = AppLog.Entry(id = 1, time = 0, message = "first", httpId = 9)
        // logs 列表新在前,导出翻转为旧在前
        val text = AppLog.exportText(listOf(newer, older))
        val firstIdx = text.indexOf("first")
        val secondIdx = text.indexOf("second")
        assertTrue(firstIdx in 0 until secondIdx)
        assertTrue(text.contains("[HTTP] first"))
        assertTrue(text.contains("[某源] second"))
    }

    @Test
    fun `导出含stacktrace缩进行`() {
        val e = entry(throwable = RuntimeException("boom"))
        val text = AppLog.exportText(listOf(e))
        assertTrue(text.contains("    java.lang.RuntimeException: boom"))
    }
}
