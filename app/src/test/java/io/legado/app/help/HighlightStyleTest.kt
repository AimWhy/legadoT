package io.legado.app.help

import com.google.gson.Gson
import io.legado.app.help.HighlightStyle.Deco
import io.legado.app.help.HighlightStyle.Kind
import io.legado.app.help.HighlightStyle.Underline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightStyleTest {

    @Test
    fun emptyStyleIsEmptyAndFastDraw() {
        val s = HighlightStyle()
        assertTrue(s.isEmpty)
        assertFalse(s.needsPerColumnDraw)
    }

    @Test
    fun fillOnlyStaysFastDraw() {
        val s = HighlightStyle(fill = 0x80FFFF00.toInt())
        assertFalse(s.isEmpty)
        assertFalse(s.needsPerColumnDraw)   // 纯背景填充不回退逐列
    }

    @Test
    fun anyDecorationNeedsPerColumnDraw() {
        assertTrue(HighlightStyle(textColor = 0xFFFF0000.toInt()).needsPerColumnDraw)
        assertTrue(HighlightStyle(bold = true).needsPerColumnDraw)
        assertTrue(HighlightStyle(italic = true).needsPerColumnDraw)
        assertTrue(HighlightStyle(underline = Underline(Kind.WAVY)).needsPerColumnDraw)
        assertTrue(HighlightStyle(strike = Deco()).needsPerColumnDraw)
        assertTrue(HighlightStyle(box = Deco()).needsPerColumnDraw)
        assertTrue(HighlightStyle(emphasis = Deco()).needsPerColumnDraw)
        assertTrue(HighlightStyle(textScale = 1.2f).needsPerColumnDraw)
    }

    @Test
    fun mergeIsPerChannelLastWins() {
        val base = HighlightStyle(fill = 1, underline = Underline(Kind.SOLID, 0))
        val other = HighlightStyle(fill = 2, textColor = 3, bold = true)
        val m = HighlightStyle.merge(base, other)
        assertEquals(2, m.fill)                       // 后者覆盖 fill
        assertEquals(3, m.textColor)                  // 后者新增 textColor
        assertTrue(m.bold)                            // 布尔取或
        assertEquals(Underline(Kind.SOLID, 0), m.underline)  // 前者保留(后者未设)
    }

    @Test
    fun mergeZeroDoesNotOverride() {
        val base = HighlightStyle(fill = 9, textColor = 8)
        val m = HighlightStyle.merge(base, HighlightStyle())  // 空样式不覆盖任何通道
        assertEquals(9, m.fill)
        assertEquals(8, m.textColor)
    }

    @Test
    fun mergeNullBaseReturnsOther() {
        val other = HighlightStyle(fill = 5)
        assertEquals(other, HighlightStyle.merge(null, other))
    }

    @Test
    fun gsonRoundTrip() {
        val gson = Gson()
        val s = HighlightStyle(
            fill = 0x80FFFF00.toInt(), textColor = 0xFFFF0000.toInt(), bold = true,
            underline = Underline(Kind.DASHED, 0xFF00FF00.toInt()),
            strike = Deco(0xFF0000FF.toInt()), emphasis = Deco()
        )
        val back = gson.fromJson(gson.toJson(s), HighlightStyle::class.java)
        assertEquals(s, back)
        assertEquals(Kind.DASHED, back.underline!!.kind)
        assertNull(back.box)
    }

    @Test
    fun fillShapeDefaultsToRounded() {
        assertEquals(HighlightStyle.FillShape.ROUNDED, HighlightStyle().fillShape)
    }

    @Test
    fun fillShapeFollowsFillOnMerge() {
        val base = HighlightStyle(fill = 1, fillShape = HighlightStyle.FillShape.MARKER)
        val other = HighlightStyle(fill = 2, fillShape = HighlightStyle.FillShape.PILL)
        val m = HighlightStyle.merge(base, other)
        assertEquals(2, m.fill)
        assertEquals(HighlightStyle.FillShape.PILL, m.fillShape)
    }

    @Test
    fun fillShapeKeptWhenOtherHasNoFill() {
        val base = HighlightStyle(fill = 1, fillShape = HighlightStyle.FillShape.HALF)
        val m = HighlightStyle.merge(base, HighlightStyle(textColor = 7))
        assertEquals(HighlightStyle.FillShape.HALF, m.fillShape)
    }

    @Test
    fun fillOnlyStillFastDrawWithShape() {
        val s = HighlightStyle(fill = 0x80FFFF00.toInt(), fillShape = HighlightStyle.FillShape.MARKER)
        assertFalse(s.needsPerColumnDraw)
    }

    @Test
    fun shapeAloneIsStillEmpty() {
        assertTrue(HighlightStyle(fillShape = HighlightStyle.FillShape.PILL).isEmpty)
    }

    @Test
    fun textScaleDefaultIsOne() {
        assertEquals(1.0f, HighlightStyle().textScale, 1e-6f)
    }

    @Test
    fun textScaleOneIsEmpty() {
        assertTrue(HighlightStyle(textScale = 1.0f).isEmpty)
    }

    @Test
    fun textScaleNonOneNeedsPerColumnDraw() {
        assertFalse(HighlightStyle(textScale = 1.0f).needsPerColumnDraw)
        assertTrue(HighlightStyle(textScale = 0.8f).needsPerColumnDraw)
        assertTrue(HighlightStyle(textScale = 1.5f).needsPerColumnDraw)
    }

    @Test
    fun textScaleMergeLastWins() {
        val base = HighlightStyle(textScale = 1.2f)
        val m = HighlightStyle.merge(base, HighlightStyle(textScale = 0.8f))
        assertEquals(0.8f, m.textScale, 1e-6f)
    }

    @Test
    fun textScaleMergeKeepsBaseWhenOtherIsOne() {
        val base = HighlightStyle(textScale = 1.5f)
        val m = HighlightStyle.merge(base, HighlightStyle(fill = 1))
        assertEquals(1.5f, m.textScale, 1e-6f)
    }

    @Test
    fun shadowNullIsEmpty() {
        assertTrue(HighlightStyle(shadow = null).isEmpty)
    }

    @Test
    fun shadowNonNullNeedsPerColumnDraw() {
        assertTrue(HighlightStyle(shadow = HighlightStyle.Shadow()).needsPerColumnDraw)
    }

    @Test
    fun shadowMergeLastWins() {
        val base = HighlightStyle(shadow = HighlightStyle.Shadow(radius = 5f))
        val other = HighlightStyle(shadow = HighlightStyle.Shadow(radius = 2f))
        val m = HighlightStyle.merge(base, other)
        assertEquals(2f, m.shadow?.radius ?: 0f, 1e-6f)
    }

    @Test
    fun shadowMergeKeepsBaseWhenOtherIsNull() {
        val base = HighlightStyle(shadow = HighlightStyle.Shadow(dx = 3f))
        val m = HighlightStyle.merge(base, HighlightStyle(fill = 1))
        assertEquals(3f, m.shadow?.dx ?: 0f, 1e-6f)
    }
}
