package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightGeometryTest {

    @Test
    fun wavePointsStartOnBaseline() {
        val pts = HighlightGeometry.wavePoints(0f, 10f, 100f, 3f, 8f, 2f)
        // n = (10/2)+1 = 6 个点 -> 12 个 float
        assertEquals(12, pts.size)
        assertEquals(0f, pts[0], 1e-4f)        // x0
        assertEquals(100f, pts[1], 1e-4f)      // sin(0)=0 -> baseY
    }

    @Test
    fun wavePointsStayWithinAmplitude() {
        val amp = 3f
        val pts = HighlightGeometry.wavePoints(0f, 40f, 50f, amp, 8f, 1f)
        var i = 1
        while (i < pts.size) {
            assertTrue(pts[i] in (50f - amp - 1e-3f)..(50f + amp + 1e-3f))
            i += 2
        }
    }

    @Test
    fun wavePointsEmptyForBadRange() {
        assertEquals(0, HighlightGeometry.wavePoints(5f, 5f, 0f, 1f, 1f, 1f).size)
    }

    @Test
    fun wavePointsReachEndpointWhenNotDivisible() {
        val pts = HighlightGeometry.wavePoints(0f, 11f, 100f, 3f, 8f, 2f)
        // 末点必须正好落在 x1=11, 否则右端漏画
        assertEquals(11f, pts[pts.size - 2], 1e-4f)
    }

    @Test
    fun wavePointsActuallyOscillate() {
        val pts = HighlightGeometry.wavePoints(0f, 32f, 100f, 4f, 8f, 1f)
        var above = false
        var below = false
        var i = 1
        while (i < pts.size) {
            if (pts[i] > 100f + 1f) above = true
            if (pts[i] < 100f - 1f) below = true
            i += 2
        }
        assertTrue(above && below)
    }

    @Test
    fun emphasisDotsCenteredPerColumn() {
        val dots = HighlightGeometry.emphasisDots(
            floatArrayOf(0f, 10f), floatArrayOf(10f, 30f), 80f, 2f
        )
        assertEquals(2, dots.size)
        assertEquals(5f, dots[0].cx, 1e-4f)    // (0+10)/2
        assertEquals(20f, dots[1].cx, 1e-4f)   // (10+30)/2
        assertEquals(80f, dots[0].cy, 1e-4f)
        assertEquals(2f, dots[0].r, 1e-4f)
    }
}
