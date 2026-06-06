package io.legado.app.ui.highlight

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.ItemHighlightRuleBinding
import io.legado.app.lib.theme.cardBackgroundColor
import io.legado.app.ui.widget.popupActionMenu
import io.legado.app.ui.widget.recycler.ItemTouchCallback

class HighlightRuleAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<HighlightRule, ItemHighlightRuleBinding>(context),
    ItemTouchCallback.Callback {

    val diffItemCallBack = object : DiffUtil.ItemCallback<HighlightRule>() {
        override fun areItemsTheSame(oldItem: HighlightRule, newItem: HighlightRule) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: HighlightRule, newItem: HighlightRule): Boolean {
            if (oldItem.getDisplayName() != newItem.getDisplayName()) return false
            if (oldItem.isEnabled != newItem.isEnabled) return false
            return true
        }

        override fun getChangePayload(oldItem: HighlightRule, newItem: HighlightRule): Any? {
            val payload = Bundle()
            if (oldItem.getDisplayName() != newItem.getDisplayName()) payload.putBoolean("upName", true)
            if (oldItem.isEnabled != newItem.isEnabled) payload.putBoolean("enabled", newItem.isEnabled)
            return if (payload.isEmpty) null else payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemHighlightRuleBinding {
        return ItemHighlightRuleBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemHighlightRuleBinding,
        item: HighlightRule,
        payloads: MutableList<Any>
    ) {
        binding.run {
            rootCard.setCardBackgroundColor(context.cardBackgroundColor)
            if (payloads.isEmpty()) {
                tvName.text = item.getDisplayName()
                swtEnabled.isChecked = item.isEnabled
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "upName" -> tvName.text = item.getDisplayName()
                            "enabled" -> swtEnabled.isChecked = item.isEnabled
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightRuleBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let {
                    it.isEnabled = isChecked
                    callBack.update(it)
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let { callBack.edit(it) }
            }
            contentLayout.setOnClickListener {
                getItem(holder.layoutPosition)?.let { callBack.edit(it) }
            }
            ivMenuMore.setOnClickListener {
                showMenu(ivMenuMore, holder.layoutPosition)
            }
        }
    }

    private fun showMenu(view: View, position: Int) {
        val item = getItem(position) ?: return
        popupActionMenu(context) {
            item(context.getString(R.string.to_top), "top")
            item(context.getString(R.string.to_bottom), "bottom")
            item(context.getString(R.string.delete), "del")
            danger("del")
        }.show(view) { action ->
            when (action) {
                "top" -> callBack.toTop(item)
                "bottom" -> callBack.toBottom(item)
                "del" -> callBack.delete(item)
            }
        }
    }

    private val movedItems = linkedSetOf<HighlightRule>()

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.order == targetItem.order) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.order
                srcItem.order = targetItem.order
                targetItem.order = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    interface CallBack {
        fun update(vararg rule: HighlightRule)
        fun delete(rule: HighlightRule)
        fun edit(rule: HighlightRule)
        fun toTop(rule: HighlightRule)
        fun toBottom(rule: HighlightRule)
        fun upOrder()
    }
}
