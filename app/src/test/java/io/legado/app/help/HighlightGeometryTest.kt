package io.legado.app.help

import io.legado.app.help.HighlightStyle.FillShape
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
    fun glyphBoxCenterRatioMatchesDefinition() {
        val expected = (0.90f - 0.16f) / 2f
        assertEquals(expected, HighlightGeometry.GLYPH_BOX_CENTER_RATIO, 1e-6f)
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

    @Test
    fun mergeFillRunsMergesAdjacentSameFillAndShape() {
        val runs = HighlightGeometry.mergeFillRuns(
            intArrayOf(1, 1, 1),
            arrayOf(FillShape.ROUNDED, FillShape.ROUNDED, FillShape.ROUNDED),
            floatArrayOf(0f, 10f, 20f),
            floatArrayOf(10f, 20f, 30f)
        )
        assertEquals(1, runs.size)
        assertEquals(0f, runs[0].x0, 1e-4f)
        assertEquals(30f, runs[0].x1, 1e-4f)
        assertEquals(1, runs[0].fill)
    }

    @Test
    fun mergeFillRunsBreaksOnColorChange() {
        val runs = HighlightGeometry.mergeFillRuns(
            intArrayOf(1, 2),
            arrayOf(FillShape.ROUNDED, FillShape.ROUNDED),
            floatArrayOf(0f, 10f), floatArrayOf(10f, 20f)
        )
        assertEquals(2, runs.size)
        assertEquals(1, runs[0].fill)
        assertEquals(2, runs[1].fill)
    }

    @Test
    fun mergeFillRunsBreaksOnShapeChange() {
        val runs = HighlightGeometry.mergeFillRuns(
            intArrayOf(1, 1),
            arrayOf(FillShape.ROUNDED, FillShape.MARKER),
            floatArrayOf(0f, 10f), floatArrayOf(10f, 20f)
        )
        assertEquals(2, runs.size)
        assertEquals(FillShape.ROUNDED, runs[0].shape)
        assertEquals(FillShape.MARKER, runs[1].shape)
    }

    @Test
    fun mergeFillRunsSkipsZeroFillAndSplitsAround() {
        val runs = HighlightGeometry.mergeFillRuns(
            intArrayOf(1, 0, 1),
            arrayOf(FillShape.ROUNDED, FillShape.ROUNDED, FillShape.ROUNDED),
            floatArrayOf(0f, 10f, 20f), floatArrayOf(10f, 20f, 30f)
        )
        assertEquals(2, runs.size)
        assertEquals(10f, runs[0].x1, 1e-4f)
        assertEquals(20f, runs[1].x0, 1e-4f)
    }

    @Test
    fun mergeFillRunsEmptyForEmptyInput() {
        assertEquals(
            0,
            HighlightGeometry.mergeFillRuns(
                IntArray(0), emptyArray(), FloatArray(0), FloatArray(0)
            ).size
        )
    }

    @Test
    fun fillBandRoundedWrapsGlyphBox() {
        val band = HighlightGeometry.fillBand(80f, 40f, 100f, FillShape.ROUNDED, 2f)
        assertTrue(band.top < 80f)      // 顶在基线之上
        assertTrue(band.bottom > 80f)   // 底在基线之下
        assertTrue(band.top >= 0f && band.bottom <= 100f)
    }

    @Test
    fun fillBandHalfCoversLowerHalfOnly() {
        val band = HighlightGeometry.fillBand(80f, 40f, 100f, FillShape.HALF, 2f)
        val rounded = HighlightGeometry.fillBand(80f, 40f, 100f, FillShape.ROUNDED, 2f)
        assertTrue(band.top > rounded.top)   // 比整字身浅
        assertTrue(band.top < 80f)
    }

    @Test
    fun fillBandBaselineSitsBelowBaseline() {
        val band = HighlightGeometry.fillBand(80f, 40f, 100f, FillShape.BASELINE, 2f)
        assertTrue(band.top >= 80f)          // 不遮字
        assertEquals(8f, band.bottom - band.top, 1e-4f)   // 厚 4dp = 8px(dp=2f)
    }

    @Test
    fun fillBandClampsToLineBox() {
        for (s in FillShape.entries) {
            val band = HighlightGeometry.fillBand(80f, 40f, 1f, s, 2f)
            assertTrue("shape=$s", band.top in 0f..1f)
            assertTrue("shape=$s", band.bottom in 0f..1f)
            assertTrue("shape=$s", band.bottom >= band.top)
        }
    }
}
