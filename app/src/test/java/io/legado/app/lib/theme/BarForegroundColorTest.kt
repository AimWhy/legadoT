package io.legado.app.lib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarForegroundColorTest {

    @Test
    fun `opaque bar judges contrast from its own background`() {
        // 不透明：栏背景=深主色，应判定为非浅色 -> 取白字
        assertFalse(
            appBarBackgroundIsLight(
                transparentActionBar = false,
                barBackgroundColor = DARK_BACKGROUND,
                contentBackgroundColor = LIGHT_BACKGROUND
            )
        )
    }

    @Test
    fun `transparent bar judges contrast from visible content background`() {
        // 沉浸式：栏透明，看到的是浅色页面背景，应判定为浅色 -> 取深色字
        assertTrue(
            appBarBackgroundIsLight(
                transparentActionBar = true,
                barBackgroundColor = DARK_BACKGROUND,
                contentBackgroundColor = LIGHT_BACKGROUND
            )
        )
    }

    @Test
    fun `transparent bar over dark content stays dark`() {
        assertFalse(
            appBarBackgroundIsLight(
                transparentActionBar = true,
                barBackgroundColor = LIGHT_BACKGROUND,
                contentBackgroundColor = DARK_BACKGROUND
            )
        )
    }

    // —— tab 配色：选中=纯黑/白、未选中=半透明，随背景明暗翻转 ——

    @Test
    fun `dark bar uses opaque white as selected and translucent white as unselected`() {
        val colors = tabTextColors(barIsLight = false)
        // 深底：选中纯白（最醒目），未选中半透明白（弱化）
        assertEquals(0xFFFFFFFF.toInt(), colors.selected)
        assertEquals(0xB3FFFFFF.toInt(), colors.unselected)
    }

    @Test
    fun `light bar uses opaque black as selected and translucent black as unselected`() {
        val colors = tabTextColors(barIsLight = true)
        // 浅底：选中纯黑，未选中半透明黑
        assertEquals(0xFF000000.toInt(), colors.selected)
        assertEquals(0x8A000000.toInt(), colors.unselected)
    }

    @Test
    fun `selected is always more opaque than unselected`() {
        for (barIsLight in listOf(true, false)) {
            val colors = tabTextColors(barIsLight)
            val selectedAlpha = colors.selected ushr 24
            val unselectedAlpha = colors.unselected ushr 24
            assertTrue(
                "selected must be more opaque than unselected (barIsLight=$barIsLight)",
                selectedAlpha > unselectedAlpha
            )
        }
    }

    private companion object {
        const val DARK_BACKGROUND = 0xFF121212.toInt()
        const val LIGHT_BACKGROUND = 0xFFFAFAFA.toInt()
    }
}
