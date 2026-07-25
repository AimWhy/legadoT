package io.legado.app.ui.highlight.edit

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.legado.app.help.HighlightGeometry
import io.legado.app.help.HighlightStyle
import io.legado.app.ui.book.read.page.HighlightDraw
import io.legado.app.utils.dpToPx

/**
 * 高亮背景形态预览,与正文渲染同源(共用 [HighlightGeometry.fillBand] 与 [HighlightDraw.drawFillRun])。
 * 把字身盒(上 0.90em / 下 0.16em)在 bounds 内纵向居中反推基线,与预览控件单行文字的位置对齐。
 */
class HighlightFillPreviewDrawable(
    private val style: HighlightStyle,
    private val textSize: Float
) : Drawable() {

    override fun draw(canvas: Canvas) {
        val fill = style.fill
        if (fill == 0) return
        val b = bounds
        val h = b.height().toFloat()
        if (h <= 0f || b.width() <= 0) return
        val baseline = h / 2f + HighlightGeometry.GLYPH_BOX_CENTER_RATIO * textSize
        val band = HighlightGeometry.fillBand(baseline, textSize, h, style.fillShape, 1f.dpToPx())
        canvas.save()
        canvas.translate(0f, b.top.toFloat())
        HighlightDraw.drawFillRun(
            canvas, b.left.toFloat(), b.right.toFloat(),
            band.top, band.bottom, fill, style.fillShape
        )
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
