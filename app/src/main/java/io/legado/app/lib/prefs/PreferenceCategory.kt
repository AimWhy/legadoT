package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.lib.theme.accentColor


class PreferenceCategory(context: Context, attrs: AttributeSet) :
    PreferenceCategory(context, attrs) {

    init {
        isPersistent = true
        layoutResource = R.layout.view_preference_category
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.findViewById(R.id.preference_title)
        if (view is TextView) {  //  && !view.isInEditMode
            view.text = title
            if (view.isInEditMode) return
            view.setTextColor(context.accentColor)
            view.isVisible = !title.isNullOrEmpty()

            // 去掉分类标题上下的色块分隔线（保留 View 占位以维持间距），提升沉浸感
            val da = holder.findViewById(R.id.preference_divider_above)
            if (da is View) {
                da.setBackgroundColor(Color.TRANSPARENT)
                da.isVisible = holder.isDividerAllowedAbove
            }
            val db = holder.findViewById(R.id.preference_divider_below)
            if (db is View) {
                db.setBackgroundColor(Color.TRANSPARENT)
                db.isVisible = holder.isDividerAllowedBelow
            }
        }
    }

}
