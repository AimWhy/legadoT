package io.legado.app.ui.book.read.page

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import io.legado.app.help.HighlightGeometry
import io.legado.app.help.HighlightStyle
import io.legado.app.utils.dpToPx

/**
 * 高亮「文字装饰」绘制。逐列装饰(着重号)与按 run 装饰(下划线/删除线/方框)。
 * 文字底色填充不在此处(见 ContentTextView.highlightPaint)。
 */
object HighlightDraw {

    private val strokePaint by lazy {
        Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    }
    private val fillPaint by lazy {
        Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    }
    private val dash by lazy { DashPathEffect(floatArrayOf(6f.dpToPx(), 4f.dpToPx()), 0f) }
    private val dot by lazy { DashPathEffect(floatArrayOf(0.5f.dpToPx(), 3.5f.dpToPx()), 0f) }
    private val wavePath = Path()
    private val linePath = Path()

    private fun lineWidth() = 1.5f.dpToPx()

    /** 用 drawPath 画水平线;硬件加速下 PathEffect(虚线/点线)只对 drawPath 生效,对 drawLine 无效(API<28)。 */
    private fun drawLinePath(canvas: Canvas, x0: Float, x1: Float, y: Float) {
        linePath.reset()
        linePath.moveTo(x0, y)
        linePath.lineTo(x1, y)
        canvas.drawPath(linePath, strokePaint)
    }

    /** 用样式配置文字 Paint(加粗/斜体)。返回需要还原的原值以便调用方复位。 */
    fun applyTextStyle(paint: Paint, style: HighlightStyle): Pair<Boolean, Float> {
        val oldBold = paint.isFakeBoldText
        val oldSkew = paint.textSkewX
        paint.isFakeBoldText = oldBold || style.bold
        if (style.italic) paint.textSkewX = -0.25f
        return oldBold to oldSkew
    }

    fun restoreTextStyle(paint: Paint, saved: Pair<Boolean, Float>) {
        paint.isFakeBoldText = saved.first
        paint.textSkewX = saved.second
    }

    /** 着重号:每列一个字下圆点(逐列调用) */
    fun drawEmphasis(canvas: Canvas, start: Float, end: Float, height: Float, color: Int) {
        val r = 1.6f.dpToPx()
        val cy = height - r - 0.5f.dpToPx()
        fillPaint.color = color
        canvas.drawCircle((start + end) / 2f, cy, r, fillPaint)
    }

    /**
     * 按 run 画线类/方框装饰。x0..x1 为连续同样式列的合并区间。
     * baseline = textLine.lineBase - textLine.lineTop;height = textLine.height。
     */
    fun drawRun(
        canvas: Canvas, x0: Float, x1: Float, baseline: Float, height: Float,
        underline: HighlightStyle.Underline?, strike: HighlightStyle.Deco?,
        box: HighlightStyle.Deco?, fallbackColor: Int
    ) {
        strokePaint.strokeWidth = lineWidth()
        strokePaint.pathEffect = null
        strokePaint.strokeCap = Paint.Cap.BUTT

        underline?.let { u ->
            val color = if (u.color != 0) u.color else fallbackColor
            strokePaint.color = color
            val y = height - 2f.dpToPx()
            when (u.kind) {
                HighlightStyle.Kind.SOLID -> canvas.drawLine(x0, y, x1, y, strokePaint)
                HighlightStyle.Kind.DOUBLE -> {
                    // 两条细线都要落在行高内:原先第二条画在 y+2dp≈height 处会被行高裁掉,看起来和单线无异
                    strokePaint.strokeWidth = 1f.dpToPx()
                    canvas.drawLine(x0, height - 3.5f.dpToPx(), x1, height - 3.5f.dpToPx(), strokePaint)
                    canvas.drawLine(x0, height - 1.5f.dpToPx(), x1, height - 1.5f.dpToPx(), strokePaint)
                    strokePaint.strokeWidth = lineWidth()
                }
                HighlightStyle.Kind.DASHED -> {
                    strokePaint.pathEffect = dash
                    drawLinePath(canvas, x0, x1, y)
                    strokePaint.pathEffect = null
                }
                HighlightStyle.Kind.DOTTED -> {
                    strokePaint.pathEffect = dot
                    strokePaint.strokeCap = Paint.Cap.ROUND
                    drawLinePath(canvas, x0, x1, y)
                    strokePaint.pathEffect = null
                    strokePaint.strokeCap = Paint.Cap.BUTT
                }
                HighlightStyle.Kind.WAVY -> {
                    val pts = HighlightGeometry.wavePoints(
                        x0, x1, y - 1f.dpToPx(), 1.5f.dpToPx(), 6f.dpToPx(), 2f.dpToPx()
                    )
                    if (pts.size >= 4) {
                        wavePath.reset()
                        wavePath.moveTo(pts[0], pts[1])
                        var i = 2
                        while (i < pts.size) { wavePath.lineTo(pts[i], pts[i + 1]); i += 2 }
                        canvas.drawPath(wavePath, strokePaint)
                    }
                }
            }
        }

        strike?.let { s ->
            strokePaint.color = if (s.color != 0) s.color else fallbackColor
            val y = baseline - (baseline) * 0.30f
            canvas.drawLine(x0, y, x1, y, strokePaint)
        }

        box?.let { bx ->
            strokePaint.color = if (bx.color != 0) bx.color else fallbackColor
            val inset = 0.5f.dpToPx()
            canvas.drawRect(x0 + inset, inset, x1 - inset, height - inset, strokePaint)
        }
    }
}
