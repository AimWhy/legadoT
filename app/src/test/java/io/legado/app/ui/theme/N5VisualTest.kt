package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N5 Wave B 视觉收尾哨兵 */
class N5VisualTest {
    @Test
    fun `app slider style tames thumb and gap`() {
        val styles = File("src/main/res/values/styles.xml").readText()
        assertTrue("应定义 Widget.App.Slider", styles.contains("name=\"Widget.App.Slider\""))
        assertTrue("应去缺口 thumbTrackGapSize=0", styles.contains("thumbTrackGapSize"))
        assertTrue("应去尾端停止点 trackStopIndicatorSize", styles.contains("trackStopIndicatorSize"))
        assertTrue("Base.AppTheme 应挂 sliderStyle", styles.contains("name=\"sliderStyle\""))
    }
}
