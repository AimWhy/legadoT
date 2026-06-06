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
}
