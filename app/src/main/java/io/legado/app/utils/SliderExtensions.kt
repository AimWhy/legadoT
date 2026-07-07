package io.legado.app.utils

import android.content.res.ColorStateList
import androidx.annotation.ColorInt
import com.google.android.material.slider.Slider

/**
 * Slider 统一施色：thumb/激活轨/未激活轨全部用双态 ColorStateList——禁用态降透明
 * （0.4，继承 ReadMenu 章节按钮 chapterTextColor 先例）。
 *
 * 为什么必须双态：BaseSlider.onTouchEvent 对 !isEnabled 静默 return false；若 tint 是
 * 状态不变的单色，被门控禁用的滑杆（亮度跟随/跟随系统语速开启时）看起来与启用态一模一样，
 * 用户会把"禁用"误读成"滑杆坏了"——真机验收实锤过一次。
 * halo 仅在交互中可见、禁用态永不出现，保持单色即可。
 *
 * @param inactiveAlpha 未激活轨启用态透明度：阅读语境手动施色=0.3，换肤引擎默认=0.24（spec 有意区分）
 */
fun Slider.applyAppTint(@ColorInt color: Int, inactiveAlpha: Float = 0.3f) {
    val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
    val main = ColorStateList(
        states,
        intArrayOf(ColorUtils.withAlpha(color, 0.4f), color)
    )
    thumbTintList = main
    trackActiveTintList = main
    trackInactiveTintList = ColorStateList(
        states,
        intArrayOf(
            ColorUtils.withAlpha(color, inactiveAlpha * 0.4f),
            ColorUtils.withAlpha(color, inactiveAlpha),
        )
    )
    haloTintList = ColorStateList.valueOf(ColorUtils.withAlpha(color, 0.2f))
}
