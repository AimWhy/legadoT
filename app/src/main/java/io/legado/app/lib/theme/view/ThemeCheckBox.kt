package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.checkbox.MaterialCheckBox

/** 行为壳:performClick 的 isUserAction 追踪+setOnUserCheckedChangeListener;施色由换肤引擎规则 A 接管(SkinInflaterFactory) */
class ThemeCheckBox(context: Context, attrs: AttributeSet) : MaterialCheckBox(context, attrs) {

    private var isUserAction = false

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
