package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import io.legado.app.utils.dpToPx

/**
 * 自绘 HSV 取色面板(N4 Task 6,M3ColorPickerDialog 内嵌组件)。
 *
 * 纵排两区:
 * - 上:色相横条(七彩 [Shader.TileMode.CLAMP] [LinearGradient],0°/60°/…/360° 红黄绿青蓝品红首尾同色)。
 * - 下:饱和度/明度方块,双 [ComposeShader] 构成——横向 白→当前色相纯色 [LinearGradient](饱和度轴,
 *   shaderB/"dst")叠加纵向 透明→黑 [LinearGradient](明度轴,shaderA/"src")以
 *   [PorterDuff.Mode.SRC_OVER] 合成:顶行透明→原样透出饱和度横条,越往下黑色不透明度越高→越暗,
 *   底行纯黑不透明→整行压黑,正是标准 SV 方块效果(与经典"横向白→hue×纵向白→黑 MULTIPLY"数学等价,
 *   这里按 spec 用 alpha 合成写法)。
 *
 * alpha 完全不在本视图职责内(由宿主对话框的 alpha Slider 独立处理):[color] 的 getter/setter
 * 只读写 HSV 三分量对应的**不透明**颜色——[Color.colorToHSV] 本身按文档就会丢弃传入色的 alpha
 * 字节,[Color.HSVToColor] 的 3 元素重载也按文档固定返回 0xFF 不透明色,因此这里无需额外维护/
 * 拼接 alpha 位,天然满足"alpha 不进入本视图"的边界。
 *
 * 只有触摸交互才会触发 [onColorChanged];外部经 [color] setter 赋值(例如宿主把 hex 输入框的
 * 合法值/色板点选同步回本面板)不会回声触发回调,由宿主自行避免双向同步环。
 */
class HsvPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** index 0=hue(0f..360f) 1=sat(0f..1f) 2=value(0f..1f) */
    private val hsv = floatArrayOf(0f, 1f, 1f)

    /** 仅触摸交互触发,外部 setter 赋值不触发(避免与宿主 hex/色板回写形成 echo 环) */
    var onColorChanged: ((Int) -> Unit)? = null

    /** 不透明色(alpha 恒 0xFF)——alpha 由宿主 Slider 独立管理,不进入本视图 */
    var color: Int
        @ColorInt get() = Color.HSVToColor(hsv)
        set(@ColorInt value) {
            Color.colorToHSV(value, hsv)
            updateSvShader()
            invalidate()
        }

    private val hueBarHeight = 28f.dpToPx()
    private val zoneGap = 16f.dpToPx()
    private val thumbRadius = 10f.dpToPx()
    private val thumbStroke = 2f.dpToPx()

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thumbStroke
        color = Color.WHITE
    }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = thumbStroke / 2f
        color = Color.BLACK
        alpha = 90
    }

    private val hueRect = RectF()
    private val svRect = RectF()

    private enum class TouchZone { NONE, HUE, SV }

    private var activeZone = TouchZone.NONE

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = paddingLeft + thumbRadius
        val right = w - paddingRight - thumbRadius
        hueRect.set(left, paddingTop.toFloat(), right, paddingTop + hueBarHeight)
        val svTop = hueRect.bottom + zoneGap
        svRect.set(
            paddingLeft.toFloat(),
            svTop,
            (w - paddingRight).toFloat(),
            (h - paddingBottom).toFloat()
        )
        buildHueShader()
        updateSvShader()
    }

    private fun buildHueShader() {
        if (hueRect.width() <= 0f) return
        // 0/60/.../360 六段七彩(首尾同红色),标准色相环横向展开
        val hueColors = IntArray(7) { i -> Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f)) }
        huePaint.shader = LinearGradient(
            hueRect.left, 0f, hueRect.right, 0f,
            hueColors, null, Shader.TileMode.CLAMP
        )
    }

    private fun updateSvShader() {
        if (svRect.width() <= 0f || svRect.height() <= 0f) return
        val pureHueColor = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        // 横向饱和度轴:白(饱和度0)→纯色相(饱和度1),全不透明,作为合成的 dst(底层)
        val satShader = LinearGradient(
            svRect.left, 0f, svRect.right, 0f,
            Color.WHITE, pureHueColor, Shader.TileMode.CLAMP
        )
        // 纵向明度轴:透明(顶,明度1,不遮挡饱和度轴)→黑(底,明度0,全压黑),作为合成的 src(上层)
        val valueShader = LinearGradient(
            0f, svRect.top, 0f, svRect.bottom,
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
        // ComposeShader(shaderA, shaderB, mode):shaderA=dst,shaderB=src——SRC_OVER 令
        // valueShader(变 alpha)盖在 satShader(全不透明)之上,顶部透出饱和度渐变、底部盖成纯黑
        svPaint.shader = ComposeShader(satShader, valueShader, PorterDuff.Mode.SRC_OVER)
    }

    override fun onDraw(canvas: Canvas) {
        if (hueRect.width() > 0f) {
            val r = hueRect.height() / 2f
            canvas.drawRoundRect(hueRect, r, r, huePaint)
            drawThumb(canvas, hueThumbX(), hueRect.centerY())
        }
        if (svRect.width() > 0f && svRect.height() > 0f) {
            canvas.drawRect(svRect, svPaint)
            drawThumb(canvas, svThumbX(), svThumbY())
        }
    }

    private fun hueThumbX() = hueRect.left + (hsv[0] / 360f) * hueRect.width()
    private fun svThumbX() = svRect.left + hsv[1] * svRect.width()
    private fun svThumbY() = svRect.top + (1f - hsv[2]) * svRect.height()

    private fun drawThumb(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, thumbRadius, thumbOuterPaint)
        canvas.drawCircle(cx, cy, thumbRadius - thumbStroke / 2f, thumbInnerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeZone = resolveZone(event.y)
                if (activeZone == TouchZone.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                handleTouch(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeZone == TouchZone.NONE) return false
                handleTouch(event.x, event.y)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeZone == TouchZone.NONE) return false
                parent?.requestDisallowInterceptTouchEvent(false)
                activeZone = TouchZone.NONE
            }

            else -> return false
        }
        return true
    }

    /** 按 y 落点归属命中区(hue/sv 纵向不重叠,两区间隙内落点就近记入 hue,再往下都归 sv) */
    private fun resolveZone(y: Float): TouchZone = when {
        hueRect.width() <= 0f -> TouchZone.NONE
        y <= hueRect.bottom + zoneGap / 2f -> TouchZone.HUE
        svRect.height() > 0f -> TouchZone.SV
        else -> TouchZone.NONE
    }

    private fun handleTouch(x: Float, y: Float) {
        when (activeZone) {
            TouchZone.HUE -> {
                val width = hueRect.width()
                if (width <= 0f) return
                val fraction = ((x - hueRect.left) / width).coerceIn(0f, 1f)
                hsv[0] = fraction * 360f
                updateSvShader()
            }

            TouchZone.SV -> {
                val width = svRect.width()
                val height = svRect.height()
                if (width <= 0f || height <= 0f) return
                val satFraction = ((x - svRect.left) / width).coerceIn(0f, 1f)
                val valFraction = (1f - (y - svRect.top) / height).coerceIn(0f, 1f)
                hsv[1] = satFraction
                hsv[2] = valFraction
            }

            TouchZone.NONE -> return
        }
        invalidate()
        onColorChanged?.invoke(color)
    }
}
