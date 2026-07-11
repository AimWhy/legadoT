package io.legado.app.ui.widget.seekbar

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

/** 竖向 Slider：子 Slider 按横向量测后旋转 -90°（底→顶增长），触摸由 View 旋转矩阵自动换算 */
class VerticalSliderWrapper @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    val slider = Slider(context).apply {
        rotation = -90f
        labelBehavior = LabelFormatter.LABEL_GONE
        valueFrom = 0f
        valueTo = 255f
        stepSize = 1f
    }

    init {
        // BaseSlider 在 RTL 下会翻转取值方向(isRtl() && !isVertical() 成立),而 View.rotation
        // 与文字方向无关;不强制 LTR 会导致 RTL 语言环境下亮度条整体上下反转
        @Suppress("DEPRECATION")
        ViewCompat.setLayoutDirection(slider, ViewCompat.LAYOUT_DIRECTION_LTR)
        addView(slider, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // 子条按"横向=本容器高度"量测,旋转后恰好竖向铺满
        slider.measure(
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST),
        )
        // 上面 super.onMeasure() 会让未旋转的子 Slider 在 AT_MOST 宽度上限下顶满(wrap_content
        // 对 Slider 等价于 fill),把这个虚高宽度烙进了本容器自身的 measuredWidth;子条按本行重新
        // 量测后,slider.measuredHeight 才是它旋转后的真实厚度,用它重新纠正自身宽度上报,
        // 避免整条撑满菜单宽度(旋转 Slider 的宽度自纠正做法)
        setMeasuredDimension(
            View.resolveSizeAndState(slider.measuredHeight + paddingLeft + paddingRight, widthMeasureSpec, 0),
            measuredHeight
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val cw = slider.measuredWidth
        val ch = slider.measuredHeight
        val l = (w - cw) / 2
        val t = (h - ch) / 2
        slider.layout(l, t, l + cw, t + ch)
    }
}
