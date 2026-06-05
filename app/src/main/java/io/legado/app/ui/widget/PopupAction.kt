package io.legado.app.ui.widget

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.utils.applyMd3PopupStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import splitties.systemservices.layoutInflater

class PopupAction(private val context: Context) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    val binding = PopupActionBinding.inflate(context.layoutInflater)
    val adapter by lazy {
        Adapter(context).apply {
            setHasStableIds(true)
        }
    }
    var onActionClick: ((action: String) -> Unit)? = null
    private var isVertical = false
    private var dangerValues: Set<String> = emptySet()

    init {
        contentView = binding.root
        applyMd3PopupStyle()

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true

        binding.recyclerView.adapter = adapter
    }

    fun setVertical(vertical: Boolean) {
        if (isVertical == vertical && binding.recyclerView.layoutManager != null) {
            return
        }
        isVertical = vertical
        binding.recyclerView.layoutManager = if (vertical) {
            LinearLayoutManager(context)
        } else {
            FlexboxLayoutManager(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
        }
        if (adapter.itemCount > 0) {
            adapter.notifyDataSetChanged()
        }
    }

    fun setDangerValues(values: Set<String>) {
        if (dangerValues == values) {
            return
        }
        dangerValues = values
        if (adapter.itemCount > 0) {
            adapter.notifyDataSetChanged()
        }
    }

    fun setItems(items: List<SelectItem<String>>) {
        adapter.setItems(items)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<SelectItem<String>, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: SelectItem<String>,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = item.title
                if (isVertical) {
                    textView.minHeight = 48.dpToPx()
                    textView.minWidth = 160.dpToPx()
                    textView.gravity = Gravity.CENTER_VERTICAL
                    textView.setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                    textView.background = ContextCompat.getDrawable(context, R.drawable.selector_menu_item_bg)
                }
                textView.setTextColor(
                    if (item.value in dangerValues) {
                        context.getCompatColor(R.color.error)
                    } else {
                        context.getCompatColor(R.color.primaryText)
                    }
                )
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    onActionClick?.invoke(item.value)
                }
            }
        }
    }

}