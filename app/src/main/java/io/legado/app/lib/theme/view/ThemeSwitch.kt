package io.legado.app.lib.theme.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.google.android.material.materialswitch.MaterialSwitch
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils

/**
 * @author Aidan Follestad (afollestad)
 */
class ThemeSwitch(context: Context, attrs: AttributeSet) : MaterialSwitch(context, attrs) {

    private var isUserAction = false

    init {
        if (!isInEditMode) {
            applyM3Tint(context.accentColor)
        }
    }

    /**
     * 用 accent 色构造 M3 开关的 thumb/track 着色，保留 MaterialSwitch 的 M3 外观。
     */
    private fun applyM3Tint(accent: Int) {
        val checkedStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        // track：选中=accent，未选中=半透明灰
        trackTintList = ColorStateList(
            checkedStates,
            intArrayOf(accent, ColorUtils.withAlpha(0x888888, 0.32f))
        )
        // thumb：选中=白，未选中=灰白
        thumbTintList = ColorStateList(
            checkedStates,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt())
        )
    }

    override fun performClick(): Boolean {
        isUserAction = true
        val result = super.performClick()
        isUserAction = false
        return result
    }

    fun setOnUserCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        if (listener == null) {
            return super.setOnCheckedChangeListener(null)
        }
        super.setOnCheckedChangeListener { _, isChecked ->
            if (isUserAction) {
                listener.invoke(isChecked)
            }
        }
    }

}
