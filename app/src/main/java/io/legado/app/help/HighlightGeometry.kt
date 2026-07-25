package io.legado.app.help

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 高亮装饰的纯几何计算(无 Android 依赖, 可 JVM 单测)。
 * 入参均为已换算好的像素值;输出坐标交给 Canvas 绘制。
 */
object HighlightGeometry {

    /**
     * 字身盒纵向中心相对基线的比例。
     * 字身盒上边界 = baseline - 0.90×textSize, 下边界 = baseline + 0.16×textSize,
     * 中心 = baseline - (0.90 - 0.16) / 2 × textSize = baseline - 0.37×textSize。
     */
    const val GLYPH_BOX_CENTER_RATIO = 0.37f

    data class Dot(val cx: Float, val cy: Float, val r: Float)

    /**
     * 波浪线采样点,返回 [x0,y0, x1,y1, ...](沿 baseY 上下振幅 amplitude, 周期 wavelength, 步进 step)。
     * 供 Canvas 用 Path 逐点连线绘制。
     */
    fun wavePoints(
        x0: Float, x1: Float, baseY: Float,
        amplitude: Float, wavelength: Float, step: Float
    ): FloatArray {
        if (x1 <= x0 || step <= 0f || wavelength <= 0f) return FloatArray(0)
        val segs = ceil((x1 - x0) / step).toInt()   // 覆盖到 x1 所需步数
        val n = segs + 1                             // 采样点数(末点落在 x1)
        val arr = FloatArray(n * 2)
        for (i in 0 until n) {
            val x = if (i == segs) x1 else x0 + i * step   // 末点夹到 x1, 防右端漏画
            val phase = (x - x0) / wavelength * (2.0 * PI)
            arr[i * 2] = x
            arr[i * 2 + 1] = (baseY + amplitude * sin(phase)).toFloat()
        }
        return arr
    }

    /** 每列一个着重点:starts/ends 为各列 x 区间,圆心取列中点 */
    fun emphasisDots(starts: FloatArray, ends: FloatArray, cy: Float, r: Float): List<Dot> {
        require(starts.size == ends.size) { "starts/ends size mismatch" }
        return List(starts.size) { i -> Dot((starts[i] + ends[i]) / 2f, cy, r) }
    }

    /** 填充的纵向范围(局部坐标, 行盒内) */
    data class Band(val top: Float, val bottom: Float)

    /** 一段连续同色同形的填充区间 */
    data class FillRun(
        val x0: Float, val x1: Float,
        val fill: Int, val shape: HighlightStyle.FillShape
    )

    /**
     * 填充的纵向范围。字身按 textSize 的固定比例取(上 0.90em / 下 0.16em),
     * 不取字体 ascent/descent —— 后者含 CJK 字体的大量行间留白, 会让色带显著高于字身。
     * dp = 1dp 对应的像素值。结果夹到 [0, height]:canvasRecorder 按 height 录制。
     */
    fun fillBand(
        baseline: Float, textSize: Float, height: Float,
        shape: HighlightStyle.FillShape, dp: Float
    ): Band {
        var top: Float
        var bottom: Float
        when (shape) {
            HighlightStyle.FillShape.HALF -> {
                top = baseline - textSize * 0.50f
                bottom = baseline + 2f * dp
            }
            HighlightStyle.FillShape.BASELINE -> {
                top = baseline + 1f * dp
                bottom = top + 4f * dp
            }
            else -> {
                top = baseline - textSize * 0.90f - 2f * dp
                bottom = baseline + textSize * 0.16f + 2f * dp
            }
        }
        top = top.coerceIn(0f, height)
        bottom = bottom.coerceIn(top, height)
        return Band(top, bottom)
    }

    /**
     * 把连续同色同形的列合并成一个区间。fill == 0 的列不产出 run 并切断合并。
     * 四个数组按列索引一一对应。
     */
    fun mergeFillRuns(
        fills: IntArray,
        shapes: Array<HighlightStyle.FillShape>,
        starts: FloatArray,
        ends: FloatArray
    ): List<FillRun> {
        val n = fills.size
        require(shapes.size == n && starts.size == n && ends.size == n) {
            "fills/shapes/starts/ends size mismatch"
        }
        val runs = ArrayList<FillRun>()
        var i = 0
        while (i < n) {
            if (fills[i] == 0) {
                i++
                continue
            }
            var j = i + 1
            while (j < n && fills[j] == fills[i] && shapes[j] == shapes[i]) j++
            runs.add(FillRun(starts[i], ends[j - 1], fills[i], shapes[i]))
            i = j
        }
        return runs
    }
}
