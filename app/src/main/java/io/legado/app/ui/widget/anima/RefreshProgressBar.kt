package io.legado.app.ui.widget.anima

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import io.legado.app.R

/**
 * 外壳保名：类名/XML 标签/公开 API 与旧手绘双层线条完全兼容，内核换 M3 LinearProgressIndicator。
 *
 * 旧控件的"主层"(durProgress/fontColor，左对齐前景条)与"副层"(secondDurProgress/secondColor，
 * 居中扩散条，也是 isAutoLoading 自动来回扫的载体)在这里合并为单一 determinate/indeterminate
 * 层：Step 1 实测消费面显示两层从未在同一实例上同时活跃使用（isAutoLoading 的 6 个消费文件从不
 * 调 setDurProgress，反之 4 个 setDurProgress 消费文件从不碰 isAutoLoading），合流不产生行为冲突。
 *
 * fontColor 在旧实现里是"主层"画笔色，也是 4 个真实调用点
 * （SearchActivity/WebViewActivity/WebViewLoginFragment/ReadRssActivity）用来把进度条染成
 * accentColor 的唯一手段。accentColor 来自 AppColorScheme（运行时可变的自定义取色引擎，见
 * MaterialValueHelper.kt），不是静态主题属性，M3 组件不会自动感知——因此没有像 speed 那样把它
 * 处理成纯占位属性，而是转发到 indicator 前景色，避免这 4 个页面的进度条退化成固定灰色。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class RefreshProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val indicator = LinearProgressIndicator(context)

    var maxProgress = 100
    var secondMaxProgress = 100
    var secondFinalProgress = 0
        private set
    var speed = 2               // 兼容属性：M3 内核下无消费，保留防编译断（10 处布局均未设置 app:speed）
    var fontColor: Int = 0
        set(value) {
            field = value
            indicator.setIndicatorColor(value)
        }
    var bgColor: Int = 0x00000000
        set(value) {
            field = value
            indicator.trackColor = value
        }
    var secondColor: Int = -0x3e3e3f
        set(value) {
            field = value
            indicator.setIndicatorColor(value)
        }

    var isAutoLoading: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // M3 约束：indeterminate 切换必须在不可见时
            indicator.visibility = INVISIBLE
            indicator.isIndeterminate = value
            indicator.visibility = VISIBLE
            if (!value) indicator.progress = 0
        }

    init {
        // 旧 styleable 解析：bg_color→trackColor、second_color→indicatorColor。
        // max_progress/dur_progress/second_dur_progress/second_max_progress/font_color/speed
        // 在全部 10 处消费布局的 XML 里均未被设置（实测 grep 确认），读取后不会改变任何行为，故不解析。
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RefreshProgressBar)
        bgColor = ta.getColor(R.styleable.RefreshProgressBar_bg_color, bgColor)
        secondColor = ta.getColor(R.styleable.RefreshProgressBar_second_color, secondColor)
        ta.recycle()
        addView(indicator, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            indicator.trackThickness = h
            indicator.trackCornerRadius = h / 2
        }
    }

    fun getDurProgress(): Int = indicator.progress

    fun setDurProgress(dur: Int) {
        exitIndeterminate()
        indicator.max = maxProgress
        indicator.setProgressCompat(dur.coerceIn(0, maxProgress), true)
    }

    fun setSecondDurProgress(dur: Int) {
        exitIndeterminate()
        secondFinalProgress = dur
        indicator.max = secondMaxProgress
        indicator.setProgressCompat(dur.coerceIn(0, secondMaxProgress), true)
    }

    private fun exitIndeterminate() {
        if (!indicator.isIndeterminate) return
        isAutoLoading = false
    }
}
