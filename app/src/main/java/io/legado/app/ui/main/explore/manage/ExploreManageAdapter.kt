package io.legado.app.ui.main.explore.manage

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.databinding.ItemExploreManageBinding
import io.legado.app.lib.theme.cardBackgroundColor
import io.legado.app.ui.widget.popupActionMenu
import io.legado.app.ui.widget.recycler.ItemTouchCallback

class ExploreManageAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<ExploreContainer, ItemExploreManageBinding>(context),
    ItemTouchCallback.Callback {

    val diffItemCallBack = object : DiffUtil.ItemCallback<ExploreContainer>() {
        override fun areItemsTheSame(oldItem: ExploreContainer, newItem: ExploreContainer) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ExploreContainer,
            newItem: ExploreContainer
        ): Boolean {
            return oldItem == newItem
        }
    }

    private fun styleText(item: ExploreContainer): String {
        return if (item.style == ExploreContainer.STYLE_LIST) {
            "${context.getString(R.string.explore_style_list)}×${item.listCount}"
        } else {
            context.getString(R.string.explore_style_flow)
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemExploreManageBinding {
        return ItemExploreManageBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemExploreManageBinding,
        item: ExploreContainer,
        payloads: MutableList<Any>
    ) {
        binding.run {
            rootCard.setCardBackgroundColor(context.cardBackgroundColor)
            tvName.text = item.getDisplayTitle()
            tvSource.text = "${item.sourceName} · ${styleText(item)}"
            swtEnabled.isChecked = item.enabled
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemExploreManageBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { isChecked ->
                getItem(holder.layoutPosition)?.let {
                    it.enabled = isChecked
                    callBack.update(it)
                }
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

    private val movedItems = linkedSetOf<ExploreContainer>()

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            if (srcItem.sortOrder == targetItem.sortOrder) {
                callBack.upOrder()
            } else {
                val srcOrder = srcItem.sortOrder
                srcItem.sortOrder = targetItem.sortOrder
                targetItem.sortOrder = srcOrder
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
        fun update(vararg container: ExploreContainer)
        fun delete(container: ExploreContainer)
        fun edit(container: ExploreContainer)
        fun toTop(container: ExploreContainer)
        fun toBottom(container: ExploreContainer)
        fun upOrder()
    }
}
