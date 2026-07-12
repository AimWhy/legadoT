package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Size
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import com.caverock.androidsvg.SVG
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("WeakerAccess", "MemberVisibilityCanBePrivate")
object SvgUtils {

    fun createBitmap(filePath: String, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            createBitmap(inputStream, width, height)
        }.getOrNull()
    }

    /**
     * 从Svg中解码bitmap
     */
    fun createBitmap(inputStream: InputStream, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            createBitmap(svg, width, height)
        }.getOrNull()
    }

    fun createBitmapFromSvgText(svgText: String, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)).use { inputStream ->
                val svg = SVG.getFromInputStream(inputStream)
                createBitmap(svg, width, height)
            }
        }.getOrNull()
    }

    fun getAspectRatioFromSvgText(svgText: String): Float? {
        return kotlin.runCatching {
            ByteArrayInputStream(svgText.toByteArray(Charsets.UTF_8)).use { inputStream ->
                val svg = SVG.getFromInputStream(inputStream)
                val size = getSize(svg)
                if (size.width > 0 && size.height > 0) {
                    size.width.toFloat() / size.height.toFloat()
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    //获取svg图片大小
    fun getSize(filePath: String): Size? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            getSize(inputStream)
        }.getOrNull()
    }

    fun getSize(inputStream: InputStream): Size? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            getSize(svg)
        }.getOrNull()
    }

    /////// private method
    private fun createBitmap(svg: SVG, width: Int? = null, height: Int? = null): Bitmap {
        val size = getSize(svg)
        // 按目标框等比适配的缩放系数(保持 SVG 宽高比);允许放大——矢量图放大也应清晰,
        // 旧逻辑只缩不放(ratio 恒 >=1),目标大于原始尺寸时仍按原小尺光栅再被 canvas 拉伸致模糊。
        val wScale = width?.takeIf { size.width > 0 }?.let { it.toFloat() / size.width }
        val hScale = height?.takeIf { size.height > 0 }?.let { it.toFloat() / size.height }
        val scale = when {
            wScale != null && hScale != null -> min(wScale, hScale)
            wScale != null -> wScale
            hScale != null -> hScale
            else -> 1f
        }

        val viewBox: RectF? = svg.documentViewBox
        if (viewBox == null && size.width > 0 && size.height > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }

        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")

        val bitmapWidth = (size.width * scale).roundToInt().coerceAtLeast(1)
        val bitmapHeight = (size.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun getSize(svg: SVG): Size {
        val width = svg.documentWidth.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.right - svg.documentViewBox.left).toInt()
        val height = svg.documentHeight.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.bottom - svg.documentViewBox.top).toInt()
        return Size(width, height)
    }
}
