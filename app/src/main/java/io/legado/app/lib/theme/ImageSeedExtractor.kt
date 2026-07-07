package io.legado.app.lib.theme

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score

/**
 * 图像种子提取：material 内置量化器（Celebi）+ Score 排序，零新依赖
 *
 * 注意：灰阶/低彩度输入时 Score 会回退 Google 蓝（0xFF4285F4）而非 null，消费方不得把非 null 种子
 * 当作"图像确有主色"的证明。
 */
@Suppress("RestrictedApi")
object ImageSeedExtractor {

    private const val SAMPLE_EDGE = 64

    @ColorInt
    fun extractSeed(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val quantized = QuantizerCelebi.quantize(pixels, 128)
        return Score.score(quantized).firstOrNull()
    }

    @ColorInt
    fun extractSeed(bitmap: Bitmap): Int? {
        val scaled = if (bitmap.width > SAMPLE_EDGE || bitmap.height > SAMPLE_EDGE) {
            Bitmap.createScaledBitmap(bitmap, SAMPLE_EDGE, SAMPLE_EDGE, false)
        } else bitmap
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        return extractSeed(pixels)
    }

    /**
     * 返回封面按 Score 排序的前 [count] 个候选主色(Score 保证降序,首个即主色)。
     * 供双色调/多色调渐变用——单色 extractSeed 丢掉了副色调,渐变因此贫瘠。
     * 空/灰阶输入返回空列表(灰阶时 Score 会给默认蓝,调用方按需守卫)。
     */
    fun extractPalette(bitmap: Bitmap, count: Int = 2): List<Int> {
        val scaled = if (bitmap.width > SAMPLE_EDGE || bitmap.height > SAMPLE_EDGE) {
            Bitmap.createScaledBitmap(bitmap, SAMPLE_EDGE, SAMPLE_EDGE, false)
        } else bitmap
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        if (scaled !== bitmap) scaled.recycle()
        if (pixels.isEmpty()) return emptyList()
        val quantized = QuantizerCelebi.quantize(pixels, 128)
        return Score.score(quantized).take(count)
    }
}
