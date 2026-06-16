package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import io.legado.app.R
import io.legado.app.databinding.ViewLabeledValueRowBinding

/**
 * 设置项「标签 + 取值」行：整行可点、点按有涟漪。取值用 [value] 读写。
 */
class LabeledValueRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewLabeledValueRowBinding.inflate(LayoutInflater.from(context), this)

    var value: CharSequence?
        get() = binding.tvValue.text
        set(v) {
            binding.tvValue.text = v
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        setBackgroundResource(outValue.resourceId)
        val a = context.obtainStyledAttributes(attrs, R.styleable.LabeledValueRow)
        binding.tvLabel.text = a.getString(R.styleable.LabeledValueRow_title)
        a.recycle()
    }
}
