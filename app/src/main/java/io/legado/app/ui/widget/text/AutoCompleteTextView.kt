package io.legado.app.ui.widget.text

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import io.legado.app.R
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.visible


@Suppress("unused")
class AutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatAutoCompleteTextView(context, attrs) {

    var delCallBack: ((value: String) -> Unit)? = null
    private var filterValues: List<String> = emptyList()

    val itemCount: Int
        get() = filterValues.size

    val selectedItemPosition: Int
        get() = filterValues.indexOf(text?.toString().orEmpty()).takeIf { it >= 0 } ?: 0

    init {
        applyTint(context.accentColor)
        setDropDownBackgroundResource(R.drawable.bg_popup_menu)
        dropDownVerticalOffset = 4.dpToPx()
        dropDownHorizontalOffset = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            isLocalePreferredLineHeightForMinimumUsed = false
        }
    }

    override fun enoughToFilter(): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            showDropDown()
        }
        return super.onTouchEvent(event)
    }

    fun setFilterValues(values: List<String>?) {
        filterValues = values.orEmpty()
        values?.let {
            setAdapter(MyAdapter(context, values))
        }
    }

    fun setFilterValues(vararg value: String) {
        filterValues = value.toList()
        setAdapter(MyAdapter(context, value.toMutableList()))
    }

    fun setSelectionByIndex(index: Int) {
        if (filterValues.isEmpty()) {
            setText("", false)
            return
        }
        val safeIndex = index.coerceIn(0, filterValues.lastIndex)
        setText(filterValues[safeIndex], false)
        setSelection(text?.length ?: 0)
    }

    inner class MyAdapter(context: Context, values: List<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, values) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_1line_text_and_del, parent, false)
            val textView = view.findViewById<TextView>(R.id.text_view)
            textView.text = getItem(position)
            val ivDelete = view.findViewById<ImageView>(R.id.iv_delete)
            if (delCallBack != null) ivDelete.visible() else ivDelete.gone()
            ivDelete.setOnClickListener {
                getItem(position)?.let {
                    remove(it)
                    delCallBack?.invoke(it)
                    showDropDown()
                }
            }
            return view
        }
    }

}
