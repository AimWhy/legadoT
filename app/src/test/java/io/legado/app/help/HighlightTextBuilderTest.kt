package io.legado.app.help

import io.legado.app.help.HighlightTextBuilder.LineInput
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightTextBuilderTest {

    @Test
    fun single_line_no_paragraph_end() {
        val s = HighlightTextBuilder.build(listOf(LineInput(listOf("abc"), 3, false)))
        assertEquals("abc", s)
    }

    @Test
    fun wrapped_lines_same_paragraph_are_contiguous() {
        val s = HighlightTextBuilder.build(
            listOf(
                LineInput(listOf("abc"), 3, false),
                LineInput(listOf("de"), 2, true)
            )
        )
        assertEquals("abcde\n", s)
    }

    @Test
    fun paragraph_end_adds_newline_for_the_plus_one() {
        val s = HighlightTextBuilder.build(
            listOf(
                LineInput(listOf("甲"), 1, true),
                LineInput(listOf("乙"), 1, true)
            )
        )
        assertEquals("甲\n乙\n", s)
        assertEquals(2, s.indexOf("乙"))
    }

    @Test
    fun pads_to_charSize_when_columns_shorter() {
        val s = HighlightTextBuilder.build(
            listOf(
                LineInput(listOf(""), 2, false),
                LineInput(listOf("X"), 1, false)
            )
        )
        assertEquals("  X", s)
        assertEquals(2, s.indexOf("X"))
    }

    @Test
    fun multiple_columns_in_a_line_concatenate() {
        val s = HighlightTextBuilder.build(listOf(LineInput(listOf("ab", "cd"), 4, false)))
        assertEquals("abcd", s)
    }

    @Test
    fun empty_input_is_empty_string() {
        assertEquals("", HighlightTextBuilder.build(emptyList()))
    }

    @Test
    fun non_text_column_contributes_zero_but_line_advances_by_charSize() {
        // 一行: 文字列 "a" + 非文字列(图片/书评映射为 "") , charSize=3 ; 下一行 "b"
        val s = HighlightTextBuilder.build(
            listOf(
                LineInput(listOf("a", ""), 3, false),
                LineInput(listOf("b"), 1, false)
            )
        )
        assertEquals("a  b", s)
        assertEquals(3, s.indexOf("b")) // 下一行起点 = 上一行 charSize
    }
}
