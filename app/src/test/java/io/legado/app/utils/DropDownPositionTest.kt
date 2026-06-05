package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DropDownPositionTest {

    /**
     * 复现 bug:底部操作栏的"更多"按钮贴在屏幕底部,下方没有空间。
     * 旧逻辑(WRAP_CONTENT)导致系统不翻转,菜单落到屏幕外不可见。
     * 期望:翻转到锚点上方,yOffset = -(popupHeight + anchorHeight + gap)。
     */
    @Test
    fun `flips above anchor when there is no room below`() {
        val gap = 12
        val popupHeight = 300
        val anchorHeight = 100
        // 锚点底部(1850 + 100 = 1950)几乎贴着可视区底部(2000)
        val yOff = resolveDropDownYOffset(
            anchorTop = 1850,
            anchorHeight = anchorHeight,
            popupHeight = popupHeight,
            frameTop = 0,
            frameBottom = 2000,
            gap = gap
        )
        assertEquals(-(popupHeight + anchorHeight + gap), yOff)
    }

    /**
     * 锚点位于顶部,下方空间充足,保持显示在下方(gap)。
     */
    @Test
    fun `stays below anchor when there is enough room below`() {
        val yOff = resolveDropDownYOffset(
            anchorTop = 200,
            anchorHeight = 100,
            popupHeight = 300,
            frameTop = 0,
            frameBottom = 2000,
            gap = 12
        )
        assertEquals(12, yOff)
    }

    /**
     * 下方放不下,但上方空间反而更小(锚点偏上的极端情况),不应翻转,保持下方让系统裁剪。
     */
    @Test
    fun `keeps below when above has even less room than below`() {
        val yOff = resolveDropDownYOffset(
            anchorTop = 50,
            anchorHeight = 100,
            popupHeight = 1900,
            frameTop = 0,
            frameBottom = 2000,
            gap = 12
        )
        assertEquals(12, yOff)
    }
}
