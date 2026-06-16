package io.legado.app.help

import io.legado.app.help.HighlightStyle.Deco
import io.legado.app.help.HighlightStyle.Kind
import io.legado.app.help.HighlightStyle.Underline
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用 App 实际使用的自定义 [GSON](注册了 Int/String 反序列化器)round-trip 高亮样式,
 * 复现「规则保存后下划线线型变回实线」。
 */
class HighlightStyleAppGsonTest {

    @Test
    fun appGsonRoundTripsAllUnderlineKinds() {
        for (k in Kind.entries) {
            val s = HighlightStyle(underline = Underline(k, 0xFF00FF00.toInt()))
            val json = GSON.toJson(s)
            val back = GSON.fromJsonObject<HighlightStyle>(json).getOrThrow()
            assertEquals("kind=$k json=$json", k, back.underline?.kind)
        }
    }

    @Test
    fun appGsonKeepsFullStyle() {
        val s = HighlightStyle(
            fill = 0x80FFFF00.toInt(), textColor = 0xFFFF0000.toInt(), bold = true,
            underline = Underline(Kind.DASHED, 0xFF00FF00.toInt()),
            strike = Deco(0xFF0000FF.toInt())
        )
        val back = GSON.fromJsonObject<HighlightStyle>(GSON.toJson(s)).getOrThrow()
        assertEquals(s, back)
    }
}
