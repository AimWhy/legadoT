package io.legado.app.ui.dict.rule

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.base.adapter.SimpleSelectableAdapter
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.ItemManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.popupActionMenu
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.dpToPx


class DictRuleAdapter(context: Context, var callBack: CallBack) :
    RecyclerAdapter<DictRule, ItemManageBinding>(context),
    ItemTouchCallback.Callback,
    SimpleSelectableAdapter<DictRule> {

    override val selectedKeys = linkedSetOf<DictRule>()

    override fun onSelectionChanged() {
        callBack.upCountView()
    }

    val diffItemCallBack = object : DiffUtil.ItemCallback<DictRule>() {

        override fun areItemsTheSame(oldItem: DictRule, newItem: DictRule): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: DictRule, newItem: DictRule): Boolean {
            if (oldItem.name != newItem.name) {
                return false
            }
            if (oldItem.enabled != newItem.enabled) {
                return false
            }
            return true
        }

        override fun getChangePayload(oldItem: DictRule, newItem: DictRule): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name) {
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

    override fun onCurrentListChanged() {
        callBack.upCountView()
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemManageBinding,
        item: DictRule,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 卡底色由换肤引擎按布局 skin_background 施加;选中态=2dp 描边
            rootCard.strokeColor = context.accentColor
            rootCard.strokeWidth = if (isSelected(item)) 2.dpToPx() else 0
            if (payloads.isEmpty()) {
                cbName.text = item.name
                swtEnabled.isChecked = item.enabled
                cbName.isChecked = isSelected(item)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbName.isChecked = isSelected(item)
                            "upName" -> cbName.text = item.name
                            "enabled" -> swtEnabled.isChecked = item.enabled
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
                    it.enabled = isChecked
                    callBack.update(it)
                }
            }
            cbName.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    setSelected(it, cbName.isChecked)
                    rootCard.strokeWidth = if (isSelected(it)) 2.dpToPx() else 0
                }
                callBack.upCountView()
            }
            ivEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    callBack.edit(it)
                }
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

    /** 删除收进 more 菜单(危险项红色),与其他管理页 item 交互一致 */
    private fun showMenu(view: View, position: Int) {
        val rule = getItem(position) ?: return
        popupActionMenu(context) {
            item(context.getString(R.string.delete), "del")
            danger("del")
        }.show(view) { action ->
            when (action) {
                "del" -> callBack.delete(rule)
            }
        }
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.sortNumber == targetItem.sortNumber) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.sortNumber
                srcItem.sortNumber = targetItem.sortNumber
                targetItem.sortNumber = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = linkedSetOf<DictRule>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<DictRule>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<DictRule> {
                return selectedKeys
            }

            override fun getItemId(position: Int): DictRule {
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
        fun update(vararg rule: DictRule)
        fun delete(rule: DictRule)
        fun edit(rule: DictRule)
        fun upOrder()
        fun upCountView()
    }
}
