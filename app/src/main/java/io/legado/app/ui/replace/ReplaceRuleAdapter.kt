package io.legado.app.ui.replace

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
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ItemManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.dpToPx


class ReplaceRuleAdapter(context: Context, var callBack: CallBack) :
    RecyclerAdapter<ReplaceRule, ItemManageBinding>(context),
    ItemTouchCallback.Callback,
    SimpleSelectableAdapter<ReplaceRule> {

    override val selectedKeys = linkedSetOf<ReplaceRule>()

    override fun onSelectionChanged() {
        callBack.upCountView()
    }

    val diffItemCallBack = object : DiffUtil.ItemCallback<ReplaceRule>() {

        override fun areItemsTheSame(oldItem: ReplaceRule, newItem: ReplaceRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ReplaceRule, newItem: ReplaceRule): Boolean {
            if (oldItem.name != newItem.name) {
                return false
            }
            if (oldItem.group != newItem.group) {
                return false
            }
            if (oldItem.isEnabled != newItem.isEnabled) {
                return false
            }
            return true
        }

        override fun getChangePayload(oldItem: ReplaceRule, newItem: ReplaceRule): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name
                || oldItem.group != newItem.group
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.isEnabled != newItem.isEnabled) {
                payload.putBoolean("enabled", newItem.isEnabled)
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

    override fun onCurrentListChanged() {
        callBack.upCountView()
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemManageBinding,
        item: ReplaceRule,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 卡底色由换肤引擎按布局 skin_background 施加;选中态=2dp 描边
            rootCard.strokeColor = context.accentColor
            rootCard.strokeWidth = if (isSelected(item)) 2.dpToPx() else 0
            if (payloads.isEmpty()) {
                cbName.text = item.getDisplayNameGroup()
                swtEnabled.isChecked = item.isEnabled
                cbName.isChecked = isSelected(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbName.isChecked = isSelected(item)
                            "upName" -> cbName.text = item.getDisplayNameGroup()
                            "enabled" -> swtEnabled.isChecked = item.isEnabled
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemManageBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let {
                    it.isEnabled = isChecked
                    callBack.update(it)
                }
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
            }
            cbName.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (cbName.isChecked) {
                        setSelected(it, true)
                    } else {
                        setSelected(it, false)
                    }
                    rootCard.strokeWidth = if (isSelected(it)) 2.dpToPx() else 0
                }
                callBack.upCountView()
            }
            ivMenuMore.setOnClickListener {
                showMenu(ivMenuMore, holder.layoutPosition)
            }
            contentLayout.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    val nowSelected = !isSelected(it)
                    setSelected(it, nowSelected)
                    cbName.isChecked = nowSelected
                    rootCard.strokeWidth = if (nowSelected) 2.dpToPx() else 0
                    callBack.upCountView()
                }
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
                "del" -> {
                    callBack.delete(item)
                    setSelected(item, false)
                }
            }
        }
    }

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

    private val movedItems = linkedSetOf<ReplaceRule>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<ReplaceRule>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<ReplaceRule> {
                return selectedKeys
            }

            override fun getItemId(position: Int): ReplaceRule {
                return getItem(position)!!
            }

            override fun updateSelectState(position: Int, isSelected: Boolean): Boolean {
                getItem(position)?.let {
                    if (isSelected) {
                        setSelected(it, true)
                    } else {
                        setSelected(it, false)
                    }
                    notifyItemChanged(position, bundleOf(Pair("selected", null)))
                    callBack.upCountView()
                    return true
                }
                return false
            }
        }

    interface CallBack {
        fun update(vararg rule: ReplaceRule)
        fun delete(rule: ReplaceRule)
        fun edit(rule: ReplaceRule)
        fun toTop(rule: ReplaceRule)
        fun toBottom(rule: ReplaceRule)
        fun upOrder()
        fun upCountView()
    }
}
