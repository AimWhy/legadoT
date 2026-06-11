package io.legado.app.ui.main.explore

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.databinding.ItemExploreContainerBinding
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.book.explore.bindSearchBook
import io.legado.app.utils.gone
import io.legado.app.utils.visible
import splitties.views.onLongClick

/**
 * 发现页容器卡片流。单 viewType:卡片内 rv_books(横滑)/ ll_books(列表)二选一显示
 */
class ExploreContainerAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<ExploreContainerState, ItemExploreContainerBinding>(context) {

    private val coverPool = RecyclerView.RecycledViewPool()

    val diffItemCallBack = object : DiffUtil.ItemCallback<ExploreContainerState>() {
        override fun areItemsTheSame(
            oldItem: ExploreContainerState,
            newItem: ExploreContainerState
        ) = oldItem.container.id == newItem.container.id

        override fun areContentsTheSame(
            oldItem: ExploreContainerState,
            newItem: ExploreContainerState
        ): Boolean {
            return oldItem.container == newItem.container
                    && oldItem.books === newItem.books
                    && oldItem.loading == newItem.loading
                    && oldItem.error == newItem.error
        }
    }

    override fun getViewBinding(parent: ViewGroup): ItemExploreContainerBinding {
        val binding = ItemExploreContainerBinding.inflate(inflater, parent, false)
        binding.rvBooks.layoutManager =
            LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        binding.rvBooks.setRecycledViewPool(coverPool)
        binding.rvBooks.adapter = ExploreCoverAdapter(context, callBack)
        binding.rlLoading.loadingColor = context.accentColor
        return binding
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemExploreContainerBinding,
        item: ExploreContainerState,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isNotEmpty()) {
                upBookshelfBadge(binding, item)
                return
            }
            val container = item.container
            tvTitle.text = container.getDisplayTitle()
            tvSource.text = container.sourceName
            if (item.loading) rlLoading.visible() else rlLoading.inVisible()
            when {
                item.books.isEmpty() && item.error != null -> {
                    rvBooks.gone()
                    llBooks.gone()
                    tvError.text = item.error
                    tvError.visible()
                    rvBooks.tag = null
                }

                container.style == ExploreContainer.STYLE_LIST -> {
                    upLightError(binding, item)
                    rvBooks.gone()
                    llBooks.visible()
                    rvBooks.tag = null
                    upListBooks(binding, item, holder)
                }

                else -> {
                    upLightError(binding, item)
                    llBooks.gone()
                    rvBooks.visible()
                    val coverAdapter = rvBooks.adapter as ExploreCoverAdapter
                    coverAdapter.onItemLongClick = {
                        showMenu(root, holder.layoutPosition)
                    }
                    val idChanged = rvBooks.tag != container.id
                    rvBooks.tag = container.id
                    coverAdapter.setBooks(item.books)
                    if (idChanged) {
                        rvBooks.scrollToPosition(0)
                    }
                }
            }
        }
    }

    /** 有旧数据时的刷新失败轻提示(完整错误信息仅在无数据的全错误态显示) */
    private fun upLightError(binding: ItemExploreContainerBinding, item: ExploreContainerState) {
        binding.tvError.run {
            if (item.error == null) {
                gone()
            } else {
                text = context.getString(R.string.explore_refresh_error)
                visible()
            }
        }
    }

    /** 列表样式:行视图复用,只 inflate 缺口、移除多余,避免每次重绑全量 removeAllViews+inflate */
    private fun upListBooks(
        binding: ItemExploreContainerBinding,
        item: ExploreContainerState,
        holder: ItemViewHolder
    ) {
        val llBooks = binding.llBooks
        val books = item.books.take(item.container.listCount)
        while (llBooks.childCount > books.size) {
            llBooks.removeViewAt(llBooks.childCount - 1)
        }
        books.forEachIndexed { index, book ->
            val rowBinding = if (index < llBooks.childCount) {
                ItemSearchBinding.bind(llBooks.getChildAt(index))
            } else {
                ItemSearchBinding.inflate(inflater, llBooks, false).also {
                    llBooks.addView(it.root)
                }
            }
            rowBinding.bindSearchBook(context, book, callBack.isInBookshelf(book))
            rowBinding.root.setOnClickListener { callBack.showBookInfo(book) }
            rowBinding.root.onLongClick {
                showMenu(binding.root, holder.layoutPosition)
            }
        }
    }

    /** 书架变化的 payload 增量:只刷角标 */
    private fun upBookshelfBadge(
        binding: ItemExploreContainerBinding,
        item: ExploreContainerState
    ) {
        binding.run {
            if (item.container.style == ExploreContainer.STYLE_LIST) {
                val books = item.books.take(item.container.listCount)
                books.forEachIndexed { index, book ->
                    if (index < llBooks.childCount) {
                        ItemSearchBinding.bind(llBooks.getChildAt(index))
                            .ivInBookshelf.isVisible = callBack.isInBookshelf(book)
                    }
                }
            } else {
                (rvBooks.adapter as? ExploreCoverAdapter)?.let {
                    it.notifyItemRangeChanged(0, it.itemCount, "isInBookshelf")
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemExploreContainerBinding) {
        binding.apply {
            tvMore.setOnClickListener {
                getItem(holder.layoutPosition)?.let { callBack.openExplore(it) }
            }
            tvError.setOnClickListener {
                getItem(holder.layoutPosition)?.let { callBack.refreshContainer(it) }
            }
            root.onLongClick {
                showMenu(root, holder.layoutPosition)
            }
        }
    }

    private fun showMenu(view: View, position: Int) {
        val item = getItem(position) ?: return
        callBack.showContainerMenu(view, item)
    }

    interface CallBack : ExploreCoverAdapter.CallBack {
        fun openExplore(state: ExploreContainerState)
        fun refreshContainer(state: ExploreContainerState)
        fun showContainerMenu(anchor: View, state: ExploreContainerState)
    }
}
