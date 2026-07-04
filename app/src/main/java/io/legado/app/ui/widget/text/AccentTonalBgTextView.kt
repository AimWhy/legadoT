package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.Selector
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

/**
 * Tonal(淡色填充)按钮:M3 secondaryContainer 底 + onSecondaryContainer 文字,圆角随 app:radius。
 */
class AccentTonalBgTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private var radius = 0

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentTonalBgTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.AccentTonalBgTextView_radius, radius)
        typedArray.recycle()
        upBackground()
    }

    fun setRadius(radius: Int) {
        this.radius = radius.dpToPx()
        upBackground()
    }

    private fun upBackground() {
        val bg: Int
        val onBg: Int
        if (isInEditMode) {
            val accent = context.getCompatColor(R.color.accent)
            bg = ColorUtils.adjustAlpha(accent, 0.12f)
            onBg = accent
        } else {
            val scheme = AppColorScheme.current
            bg = scheme.secondaryContainer
            onBg = scheme.onSecondaryContainer
        }
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setDefaultBgColor(bg)
            .setPressedBgColor(ColorUtils.darkenColor(bg))
            .create()
        setTextColor(onBg)
    }
}
