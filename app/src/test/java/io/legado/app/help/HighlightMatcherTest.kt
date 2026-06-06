package io.legado.app.help

import io.legado.app.help.HighlightMatcher.LineSpec
import io.legado.app.help.HighlightMatcher.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightMatcherTest {

    // 单行 5 列(每列 1 字), 无段末; 高亮覆盖第 1..2 列(章内 [1,3))
    @Test
    fun singleLine_partialRange() {
        val lines = listOf(LineSpec(charSize = 5, columnCharLengths = listOf(1, 1, 1, 1, 1), isParagraphEnd = false))
        val res = HighlightMatcher.resolve(0, lines, listOf(Range(1, 3, HighlightStyle(fill = 0xAA, textColor = 0xBB))))
        assertEquals(listOf(0, 0xAA, 0xAA, 0, 0), res[0].map { it?.fill ?: 0 })
        assertEquals(listOf(0, 0xBB, 0xBB, 0, 0), res[0].map { it?.textColor ?: 0 })
    }

    // 半开区间: range [0,2) 命中第 0..1 列, 不含第 2 列
    @Test
    fun halfOpen_endExclusive() {
        val lines = listOf(LineSpec(3, listOf(1, 1, 1), false))
        val res = HighlightMatcher.resolve(0, lines, listOf(Range(0, 2, HighlightStyle(fill = 0x11))))
        assertEquals(listOf(0x11, 0x11, 0), res[0].map { it?.fill ?: 0 })
    }

    // pageBase 偏移 + 段末 +1: 第二行从 (16+1=17) 起算
    @Test
    fun paragraphEnd_advancesByCharSizePlusOne() {
        val lines = listOf(
            LineSpec(6, listOf(1, 1, 1, 1, 1, 1), isParagraphEnd = true), // 章内 [10,16), 段末占 16
            LineSpec(3, listOf(1, 1, 1), false)                            // 章内从 17 起: 17,18,19
        )
        val res = HighlightMatcher.resolve(10, lines, listOf(Range(18, 19, HighlightStyle(fill = 0x77))))
        assertEquals(listOf(0, 0, 0, 0, 0, 0), res[0].map { it?.fill ?: 0 })
        assertEquals(listOf(0, 0x77, 0), res[1].map { it?.fill ?: 0 })
    }

    // 多列字(charData.length>1): 一列占 2 个字符位
    @Test
    fun multiCharColumn() {
        val lines = listOf(LineSpec(3, listOf(2, 1), false)) // 第0列占[0,2), 第1列占[2,3)
        val res = HighlightMatcher.resolve(0, lines, listOf(Range(1, 2, HighlightStyle(fill = 0x22))))
        assertEquals(listOf(0x22, 0), res[0].map { it?.fill ?: 0 })
    }

    // 多区间重叠: 后者按通道覆盖前者(fill 被覆盖, textColor 0 不覆盖)
    @Test
    fun overlappingRanges_lastWins() {
        val lines = listOf(LineSpec(3, listOf(1, 1, 1), false))
        val res = HighlightMatcher.resolve(
            0, lines,
            listOf(Range(0, 3, HighlightStyle(fill = 0x01, textColor = 0x0A)), Range(1, 2, HighlightStyle(fill = 0x02)))
        )
        assertEquals(0x02, res[0][1]!!.fill)
        assertEquals(0x0A, res[0][1]!!.textColor)
    }

    // 按通道合并: fill 来自前者, textColor/bold 来自后者
    @Test
    fun overlappingRangesMergePerChannel() {
        val lines = listOf(LineSpec(3, listOf(1, 1, 1), false))
        val ranges = listOf(
            Range(0, 3, HighlightStyle(fill = 11)),
            Range(1, 2, HighlightStyle(textColor = 22, bold = true))
        )
        val r = HighlightMatcher.resolve(0, lines, ranges)
        assertEquals(11, r[0][0]!!.fill)
        assertEquals(0, r[0][0]!!.textColor)
        assertEquals(11, r[0][1]!!.fill)
        assertEquals(22, r[0][1]!!.textColor)
        assertTrue(r[0][1]!!.bold)
        assertEquals(11, r[0][2]!!.fill)
    }

    // 空区间 → 全 null(无高亮)
    @Test
    fun emptyRanges_allNull() {
        val lines = listOf(LineSpec(2, listOf(1, 1), false))
        val res = HighlightMatcher.resolve(0, lines, emptyList())
        assertEquals(listOf<HighlightStyle?>(null, null), res[0])
    }

    // 非文字列(len=0)不被上色
    @Test
    fun zeroLengthColumn_notColored() {
        val lines = listOf(LineSpec(2, listOf(0, 1, 1), false))
        val res = HighlightMatcher.resolve(0, lines, listOf(Range(0, 2, HighlightStyle(fill = 0x33))))
        assertEquals(listOf(0, 0x33, 0x33), res[0].map { it?.fill ?: 0 })
    }
}
