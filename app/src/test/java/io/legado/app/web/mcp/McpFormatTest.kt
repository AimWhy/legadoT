package io.legado.app.web.mcp

import io.legado.app.data.entities.BookSource
import org.htmlunit.corejs.javascript.Undefined
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpFormatTest {

    @Test
    fun detectFormatBoundary() {
        assertEquals("json", McpFormat.detectFormat("  {\"a\":1}"))
        assertEquals("json", McpFormat.detectFormat("[1]"))
        assertEquals("js", McpFormat.detectFormat("// @name x"))
        assertEquals("js", McpFormat.detectFormat(""))
    }

    @Test
    fun summarizeFilterAndShape() {
        val a = BookSource(bookSourceName = "起点", bookSourceUrl = "https://a.com")
        val b = BookSource(bookSourceName = "笔趣", bookSourceUrl = "https://b.com")
        val all = McpFormat.summarizeSources(listOf(a, b), null)
        assertEquals(2, all.size)
        assertEquals("起点", all[0]["bookSourceName"])
        assertEquals(false, all[0]["isJsSource"])
        val hit = McpFormat.summarizeSources(listOf(a, b), "B.COM")
        assertEquals(1, hit.size)
        assertEquals("https://b.com", hit[0]["bookSourceUrl"])
    }

    @Test
    fun truncateBoundary() {
        assertEquals("abc", McpFormat.truncate("abc", 5))
        val cut = McpFormat.truncate("abcdef", 5)
        assertTrue(cut.startsWith("abcde"))
        assertTrue(cut.contains("已截断,原文 6 字符"))
    }

    @Test
    fun renderEvalResultScalars() {
        assertEquals("abc", McpFormat.renderEvalResult("abc"))
        assertEquals("null", McpFormat.renderEvalResult(null))
        assertEquals("undefined", McpFormat.renderEvalResult(Undefined.instance))
        assertEquals("true", McpFormat.renderEvalResult(true))
        assertEquals("42", McpFormat.renderEvalResult(42.0))
        assertEquals("1.5", McpFormat.renderEvalResult(1.5))
    }

    @Test
    fun renderEvalResultJsonShape() {
        val rendered = McpFormat.renderEvalResult(mapOf("a" to listOf(1.0, "x"), "b" to true))
        assertEquals(
            """
            {
              "a": [
                1,
                "x"
              ],
              "b": true
            }
            """.trimIndent(),
            rendered
        )
    }

    @Test
    fun renderEvalResultLeafFallback() {
        val rendered = McpFormat.renderEvalResult(Any())
        assertTrue(rendered.endsWith("(Object)"))
    }

    @Test
    fun renderEvalResultCyclicFallsBack() {
        val m = HashMap<String, Any>()
        m["self"] = m
        val rendered = McpFormat.renderEvalResult(m)
        assertTrue(rendered.endsWith("(HashMap)"))
    }
}
