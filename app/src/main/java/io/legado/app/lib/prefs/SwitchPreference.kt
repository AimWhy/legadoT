package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.materialswitch.MaterialSwitch
import io.legado.app.R

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
        // MaterialSwitch 施色由引擎规则 A 在 inflate 时接管(同值同式,R2d 删手动双写)
        Preference.bindView<MaterialSwitch>(
            context, holder, icon, title, summary,
            widgetLayoutResource,
            androidx.preference.R.id.switchWidget,
            isBottomBackground = isBottomBackground
        )
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
