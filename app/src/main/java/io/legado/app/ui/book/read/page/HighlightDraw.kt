package io.legado.app.ui.book.read.page

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import io.legado.app.help.HighlightGeometry
import io.legado.app.help.HighlightStyle
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.dpToPx

/**
 * 高亮「文字装饰」绘制。逐列装饰(着重号)、按 run 装饰(下划线/删除线/方框/背景填充)。
 */
object HighlightDraw {

    private val strokePaint by lazy {
        Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    }
    private val fillPaint by lazy {
        Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    }
    private val dash by lazy { DashPathEffect(floatArrayOf(6f.dpToPx(), 4f.dpToPx()), 0f) }
    private val dot by lazy { DashPathEffect(floatArrayOf(2f.dpToPx(), 3f.dpToPx()), 0f) }
    private val wavePath = Path()
    private val fillPath = Path()

    private fun lineWidth() = 1.5f.dpToPx()

    /** 按比例缩放 ARGB 的 alpha 通道 */
    private fun scaleAlpha(color: Int, factor: Float): Int {
        val a = ((color ushr 24) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /** applyTextStyle 需还原的原始 Paint 状态 */
    class SavedTextStyle(val bold: Boolean, val skew: Float, val typeface: Typeface?, val textSize: Float)

    /** 用样式配置文字 Paint(加粗/斜体/字号缩放/自定义字体)。返回需要还原的原值以便调用方复位。 */
    fun applyTextStyle(paint: Paint, style: HighlightStyle): SavedTextStyle {
        val saved = SavedTextStyle(paint.isFakeBoldText, paint.textSkewX, paint.typeface, paint.textSize)
        paint.isFakeBoldText = saved.bold || style.bold
        if (style.italic) paint.textSkewX = -0.25f
        if (style.textScale != 1.0f) paint.textSize = saved.textSize * style.textScale
        if (style.fontPath.isNotEmpty()) {
            ChapterProvider.getHighlightTypeface(style.fontPath)?.let { paint.typeface = it }
        }
        return saved
    }

    fun restoreTextStyle(paint: Paint, saved: SavedTextStyle) {
        paint.isFakeBoldText = saved.bold
        paint.textSkewX = saved.skew
        paint.typeface = saved.typeface
        paint.textSize = saved.textSize
    }

    /**
     * 画一段合并后的背景填充。x0..x1 为连续同色同形列的合并区间,
     * top..bottom 由 [HighlightGeometry.fillBand] 给出(行盒局部坐标)。
     */
    fun drawFillRun(
        canvas: Canvas, x0: Float, x1: Float, top: Float, bottom: Float,
        fill: Int, shape: HighlightStyle.FillShape
    ) {
        if (x1 <= x0 || bottom <= top) return
        fillPaint.shader = null
        when (shape) {
            HighlightStyle.FillShape.ROUNDED -> {
                val r = 3f.dpToPx()
                fillPaint.color = fill
                canvas.drawRoundRect(x0, top, x1, bottom, r, r, fillPaint)
            }

            HighlightStyle.FillShape.MARKER -> {
                // 两端 alpha 渐隐 + 四角圆角不等 → 手涂痕迹
                fillPaint.color = fill
                fillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(fill and 0x00FFFFFF, fill, fill, fill and 0x00FFFFFF),
                    floatArrayOf(0f, 0.04f, 0.96f, 1f),
                    Shader.TileMode.CLAMP
                )
                val u = 2f.dpToPx()
                fillPath.reset()
                fillPath.addRoundRect(
                    x0, top, x1, bottom,
                    floatArrayOf(u, u * 2f, u * 2.5f, u, u * 1.5f, u * 2f, u * 2f, u),
                    Path.Direction.CW
                )
                canvas.drawPath(fillPath, fillPaint)
                fillPaint.shader = null
            }

            HighlightStyle.FillShape.HALF,
            HighlightStyle.FillShape.BASELINE -> {
                fillPaint.color = fill
                canvas.drawRect(x0, top, x1, bottom, fillPaint)
            }

            HighlightStyle.FillShape.PILL -> {
                val r = (bottom - top) / 2f
                fillPaint.color = scaleAlpha(fill, 0.35f)
                canvas.drawRoundRect(x0, top, x1, bottom, r, r, fillPaint)
                strokePaint.strokeWidth = 1f.dpToPx()
                strokePaint.pathEffect = null
                strokePaint.color = fill or 0xFF000000.toInt()
                canvas.drawRoundRect(x0, top, x1, bottom, r, r, strokePaint)
            }
        }
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
        canvas: Canvas, x0: Float, x1: Float, baseline: Float, height: Float, textSize: Float,
        underline: HighlightStyle.Underline?, strike: HighlightStyle.Deco?,
        box: HighlightStyle.Deco?, fallbackColor: Int
    ) {
        strokePaint.strokeWidth = lineWidth()
        strokePaint.pathEffect = null

        underline?.let { u ->
            val color = if (u.color != 0) u.color else fallbackColor
            strokePaint.color = color
            val y = height - 2f.dpToPx()
            when (u.kind) {
                HighlightStyle.Kind.SOLID -> canvas.drawLine(x0, y, x1, y, strokePaint)
                HighlightStyle.Kind.DOUBLE -> {
                    canvas.drawLine(x0, y, x1, y, strokePaint)
                    canvas.drawLine(x0, y + 2f.dpToPx(), x1, y + 2f.dpToPx(), strokePaint)
                }
                HighlightStyle.Kind.DASHED -> {
                    strokePaint.pathEffect = dash
                    canvas.drawLine(x0, y, x1, y, strokePaint)
                    strokePaint.pathEffect = null
                }
                HighlightStyle.Kind.DOTTED -> {
                    strokePaint.pathEffect = dot
                    canvas.drawLine(x0, y, x1, y, strokePaint)
                    strokePaint.pathEffect = null
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
            val y = baseline - textSize * HighlightGeometry.GLYPH_BOX_CENTER_RATIO
            canvas.drawLine(x0, y, x1, y, strokePaint)
        }

        box?.let { bx ->
            strokePaint.color = if (bx.color != 0) bx.color else fallbackColor
            val dp = 1f.dpToPx()
            val band = HighlightGeometry.fillBand(baseline, textSize, height, HighlightStyle.FillShape.ROUNDED, dp)
            val r = 3f.dpToPx()  // 与 drawFillRun ROUNDED 分支相同
            val halfStroke = strokePaint.strokeWidth / 2f
            canvas.drawRoundRect(
                x0 + halfStroke, band.top + halfStroke,
                x1 - halfStroke, band.bottom - halfStroke,
                r, r, strokePaint
            )
        }
    }
}
