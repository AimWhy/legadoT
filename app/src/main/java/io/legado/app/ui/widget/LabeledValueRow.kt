package io.legado.app.ui.widget

import android.content.Context
import android.text.TextUtils
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
    private var valueWidthFraction: Float? = null

    var value: CharSequence?
        get() = binding.tvValue.text
        set(v) {
            binding.tvValue.text = v
        }

    fun constrainValueWidth(maxRowFraction: Float = 0.5f) {
        require(maxRowFraction > 0f)
        valueWidthFraction = maxRowFraction.coerceAtMost(0.5f)
        binding.tvValue.isSingleLine = true
        binding.tvValue.ellipsize = TextUtils.TruncateAt.END
        updateValueMaxWidth(width)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateValueMaxWidth(right - left)
    }

    private fun updateValueMaxWidth(rowWidth: Int) {
        val fraction = valueWidthFraction ?: return
        if (rowWidth > 0) {
            val targetWidth = (rowWidth * fraction).toInt()
            if (binding.tvValue.maxWidth != targetWidth) {
                binding.tvValue.maxWidth = targetWidth
            }
        }
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
