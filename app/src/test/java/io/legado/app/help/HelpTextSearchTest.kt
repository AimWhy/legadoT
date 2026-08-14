package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpTextSearchTest {

    @Test
    fun basicMatch() {
        assertEquals(listOf(3..4), findTextRanges("abc测试def", "测试"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(listOf(0..4), findTextRanges("JsLib", "jslib"))
        assertEquals(listOf(0..4, 6..10), findTextRanges("JsLib jslib", "jslib"))
    }

    @Test
    fun multipleOccurrencesNonOverlapping() {
        val ranges = findTextRanges("aaaa", "aa")
        assertEquals(listOf(0..1, 2..3), ranges)
    }

    @Test
    fun regexMetaCharsAreLiteral() {
        val ranges = findTextRanges("a.b 与 aXb", "a.b")
        assertEquals(listOf(0..2), ranges)
    }

    @Test
    fun blankQueryReturnsEmpty() {
        assertTrue(findTextRanges("任意内容", "").isEmpty())
        assertTrue(findTextRanges("任意内容", "   ").isEmpty())
    }

    @Test
    fun unmatchedQueryReturnsEmpty() {
        assertTrue(findTextRanges("书源规则", "jsLib").isEmpty())
    }
}
