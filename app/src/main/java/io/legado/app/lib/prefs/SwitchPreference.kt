package io.legado.app.lib.prefs

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.materialswitch.MaterialSwitch
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.ColorUtils

class SwitchPreference(context: Context, attrs: AttributeSet) :
    SwitchPreferenceCompat(context, attrs) {

    private val isBottomBackground: Boolean
    private var onLongClick: ((preference: SwitchPreference) -> Boolean)? = null

    init {
        layoutResource = R.layout.view_preference
        // 用 M3 MaterialSwitch 作为开关 widget
        widgetLayoutResource = R.layout.view_preference_widget_switch
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Preference)
        isBottomBackground = typedArray.getBoolean(R.styleable.Preference_isBottomBackground, false)
        typedArray.recycle()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val v = Preference.bindView<MaterialSwitch>(
            context, holder, icon, title, summary,
            widgetLayoutResource,
            androidx.preference.R.id.switchWidget,
            isBottomBackground = isBottomBackground
        )
        if (v is MaterialSwitch && !v.isInEditMode) {
            val accent = context.accentColor
            v.trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, ColorUtils.withAlpha(0x888888, 0.32f))
            )
            v.thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt())
            )
        }
        super.onBindViewHolder(holder)
        onLongClick?.let { listener ->
            holder.itemView.setOnLongClickListener {
                listener.invoke(this)
            }
        }
    }

    fun onLongClick(listener: (preference: SwitchPreference) -> Boolean) {
        onLongClick = listener
    }

}
