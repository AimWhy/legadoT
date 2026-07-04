package io.legado.app.ui.rss.source.manage

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.ui.widget.popupActionMenu
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.base.adapter.SimpleSelectableAdapter
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ItemManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.dpToPx
import java.util.Collections


class RssSourceAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RssSource, ItemManageBinding>(context),
    ItemTouchCallback.Callback,
    SimpleSelectableAdapter<RssSource> {

    override val selectedKeys = linkedSetOf<RssSource>()

    override fun onSelectionChanged() {
        callBack.upCountView()
    }

    val diffItemCallback = object : DiffUtil.ItemCallback<RssSource>() {

        override fun areItemsTheSame(oldItem: RssSource, newItem: RssSource): Boolean {
            return oldItem.sourceUrl == newItem.sourceUrl
        }

        override fun areContentsTheSame(oldItem: RssSource, newItem: RssSource): Boolean {
            return oldItem.sourceName == newItem.sourceName
                    && oldItem.sourceGroup == newItem.sourceGroup
                    && oldItem.enabled == newItem.enabled
        }

        override fun getChangePayload(oldItem: RssSource, newItem: RssSource): Any? {
            val payload = Bundle()
            if (oldItem.sourceName != newItem.sourceName
                || oldItem.sourceGroup != newItem.sourceGroup
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enabled != newItem.enabled) {
                payload.putBoolean("enabled", newItem.enabled)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemManageBinding {
        return ItemManageBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemManageBinding,
        item: RssSource,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 卡底色由换肤引擎按布局 skin_background 施加;选中态=2dp 描边
            rootCard.strokeColor = context.accentColor
            rootCard.strokeWidth = if (isSelected(item)) 2.dpToPx() else 0
            if (payloads.isEmpty()) {
                cbName.text = item.getDisplayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbName.isChecked = isSelected(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "upName" -> cbName.text = item.getDisplayNameGroup()
                            "enabled" -> swtEnabled.isChecked = bundle.getBoolean("enabled")
                            "selected" -> cbName.isChecked = isSelected(item)
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemManageBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    it.enabled = checked
                    callBack.update(it)
                }
            }
            cbName.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    setSelected(it, checked)
                    rootCard.strokeWidth = if (checked) 2.dpToPx() else 0
                    callBack.upCountView()
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
            ivMenuMore.setOnClickListener {
                showMenu(ivMenuMore, holder.layoutPosition)
            }
        }
    }

    override fun onCurrentListChanged() {
        callBack.upCountView()
    }

    fun checkSelectedInterval() {
        val selectedPosition = linkedSetOf<Int>()
        getItems().forEachIndexed { index, it ->
            if (isSelected(it)) {
                selectedPosition.add(index)
            }
        }
        val minPosition = Collections.min(selectedPosition)
        val maxPosition = Collections.max(selectedPosition)
        val itemCount = maxPosition - minPosition + 1
        for (i in minPosition..maxPosition) {
            getItem(i)?.let {
                setSelected(it, true)
            }
        }
        notifyItemRangeChanged(minPosition, itemCount, bundleOf(Pair("selected", null)))
        callBack.upCountView()
    }

    private fun showMenu(view: View, position: Int) {
        val source = getItem(position) ?: return
        popupActionMenu(context) {
            item(context.getString(R.string.to_top), "top")
            item(context.getString(R.string.to_bottom), "bottom")
            item(context.getString(R.string.delete), "del")
            danger("del")
        }.show(view) { action ->
            when (action) {
                "top" -> callBack.toTop(source)
                "bottom" -> callBack.toBottom(source)
                "del" -> {
                    callBack.del(source)
                    setSelected(source, false)
                }
            }
        }
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.customOrder == targetItem.customOrder) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.customOrder
                srcItem.customOrder = targetItem.customOrder
                targetItem.customOrder = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = hashSetOf<RssSource>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<RssSource>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<RssSource> {
                return selectedKeys
            }

            override fun getItemId(position: Int): RssSource {
                return getItem(position)!!
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let {
                    setSelected(it, isSelected)
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }

    interface CallBack {
        fun del(source: RssSource)
        fun edit(source: RssSource)
        fun update(vararg source: RssSource)
        fun toTop(source: RssSource)
        fun toBottom(source: RssSource)
        fun upOrder()
        fun upCountView()
    }
}
