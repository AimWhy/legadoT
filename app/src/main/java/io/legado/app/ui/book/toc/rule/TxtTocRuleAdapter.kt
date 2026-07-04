package io.legado.app.ui.book.toc.rule

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
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.databinding.ItemManageBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.widget.popupActionMenu
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.dpToPx
import io.legado.app.utils.visible

class TxtTocRuleAdapter(context: Context, private val callBack: CallBack) :
    RecyclerAdapter<TxtTocRule, ItemManageBinding>(context),
    ItemTouchCallback.Callback,
    SimpleSelectableAdapter<TxtTocRule> {

    override val selectedKeys = linkedSetOf<TxtTocRule>()

    override fun onSelectionChanged() {
        callBack.upCountView()
    }

    val diffItemCallBack = object : DiffUtil.ItemCallback<TxtTocRule>() {

        override fun areItemsTheSame(oldItem: TxtTocRule, newItem: TxtTocRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TxtTocRule, newItem: TxtTocRule): Boolean {
            if (oldItem.name != newItem.name) {
                return false
            }
            if (oldItem.enable != newItem.enable) {
                return false
            }
            if (oldItem.example != newItem.example) {
                return false
            }
            return true
        }

        override fun getChangePayload(oldItem: TxtTocRule, newItem: TxtTocRule): Any? {
            val payload = Bundle()
            if (oldItem.name != newItem.name) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enable != newItem.enable) {
                payload.putBoolean("enabled", newItem.enable)
            }
            if (oldItem.example != newItem.example) {
                payload.putBoolean("upExample", true)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemManageBinding {
        return ItemManageBinding.inflate(inflater, parent, false).apply {
            tvSubtitle.visible()
        }
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemManageBinding,
        item: TxtTocRule,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 卡底色由换肤引擎按布局 skin_background 施加;选中态=2dp 描边
            rootCard.strokeColor = context.accentColor
            rootCard.strokeWidth = if (isSelected(item)) 2.dpToPx() else 0
            if (payloads.isEmpty()) {
                cbName.text = item.name
                swtEnabled.isChecked = item.enable
                cbName.isChecked = isSelected(item)
                tvSubtitle.text = item.example
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "selected" -> cbName.isChecked = isSelected(item)
                            "upName" -> cbName.text = item.name
                            "upExample" -> tvSubtitle.text = item.example
                            "enabled" -> swtEnabled.isChecked = item.enable
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemManageBinding) {
        binding.cbName.setOnUserCheckedChangeListener { isChecked ->
            getItem(holder.layoutPosition)?.let {
                setSelected(it, isChecked)
                binding.rootCard.strokeWidth = if (isChecked) 2.dpToPx() else 0
                callBack.upCountView()
            }
        }
        binding.swtEnabled.setOnUserCheckedChangeListener { isChecked ->
            getItem(holder.layoutPosition)?.let {
                it.enable = isChecked
                callBack.update(it)
            }
        }
        binding.ivEdit.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.edit(it)
            }
        }
        binding.ivMenuMore.setOnClickListener {
            showMenu(it, holder.layoutPosition)
        }
        binding.contentLayout.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                val nowSelected = !isSelected(it)
                setSelected(it, nowSelected)
                binding.cbName.isChecked = nowSelected
                binding.rootCard.strokeWidth = if (nowSelected) 2.dpToPx() else 0
                callBack.upCountView()
            }
        }
    }

    override fun onCurrentListChanged() {
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
            if (srcItem.serialNumber == targetItem.serialNumber) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.serialNumber
                srcItem.serialNumber = targetItem.serialNumber
                targetItem.serialNumber = srcOrder
                movedItems.add(srcItem)
                movedItems.add(targetItem)
            }
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = hashSetOf<TxtTocRule>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            callBack.update(*movedItems.toTypedArray())
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<TxtTocRule>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<TxtTocRule> {
                return selectedKeys
            }

            override fun getItemId(position: Int): TxtTocRule {
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
        fun del(source: TxtTocRule)
        fun edit(source: TxtTocRule)
        fun update(vararg source: TxtTocRule)
        fun toTop(source: TxtTocRule)
        fun toBottom(source: TxtTocRule)
        fun upOrder()
        fun upCountView()
    }

}