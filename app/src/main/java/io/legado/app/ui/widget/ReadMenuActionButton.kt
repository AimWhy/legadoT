package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.databinding.ViewReadMenuButtonBinding
import io.legado.app.utils.dpToPx

/**
 * 阅读菜单图标动作按钮：图标 + 可选文字标签。
 * 统一触摸区 / 圆角涟漪 / 着色，取代散落的 ImageView + setColorFilter。
 */
class ReadMenuActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding =
        ViewReadMenuButtonBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        minimumWidth = 48.dpToPx()
        minimumHeight = 48.dpToPx()
        isClickable = true
        isFocusable = true
        setBackgroundResource(R.drawable.bg_read_menu_action)
        val pad = 8.dpToPx()
        setPadding(pad, pad, pad, pad)

        val a = context.obtainStyledAttributes(attrs, R.styleable.ReadMenuActionButton)
        val iconRes = a.getResourceId(R.styleable.ReadMenuActionButton_menuIcon, 0)
        if (iconRes != 0) binding.ivIcon.setImageResource(iconRes)
        a.getString(R.styleable.ReadMenuActionButton_menuLabel)?.let { binding.tvLabel.text = it }
        binding.tvLabel.isVisible =
            a.getBoolean(R.styleable.ReadMenuActionButton_menuShowLabel, false)
        a.recycle()
    }

    fun setIconResource(@DrawableRes resId: Int) {
        binding.ivIcon.setImageResource(resId)
    }

    /** 统一着色图标（标签若显示一并着色）。供 ReadMenu 沉浸/普通模式传入计算好的前景色。 */
    fun setTint(color: Int) {
        binding.ivIcon.setColorFilter(color)
        binding.tvLabel.setTextColor(color)
    }
}
