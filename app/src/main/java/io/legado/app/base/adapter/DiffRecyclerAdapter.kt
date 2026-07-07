package io.legado.app.base.adapter

import android.content.Context
import android.os.Parcelable
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import splitties.views.onLongClick

/**
 * Created by Invincible on 2017/12/15.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class DiffRecyclerAdapter<ITEM, VB : ViewBinding>(protected val context: Context) :
    RecyclerView.Adapter<ItemViewHolder>() {

    val inflater: LayoutInflater = LayoutInflater.from(context)

    // N3a: 详情页内嵌目录复用本类装配 header(卡片信息区+目录区头),原类只有纯列表,
    // 无 header/footer 概念;此处照搬 RecyclerAdapter 的 headerItems 方案,新增能力对
    // 已有零 header 的使用方(SearchAdapter/ChangeSourceAdapter/ChapterListFragment)零影响
    private val headerItems: SparseArray<(parent: ViewGroup) -> ViewBinding> by lazy { SparseArray() }

    // N3a 修复: AsyncListDiffer(this, diffItemCallback) 内部会用 AdapterListUpdateCallback 包装 this,
    // 其 onInserted/onRemoved/onMoved/onChanged 把 submitList 产生的 position 原样转发给
    // notifyItemRange*/notifyItemMoved,不做任何 header 偏移(验证过 recyclerview 1.2.1 源码确实如此)。
    // 本类有 header 后(详情页内嵌目录 2 个 header),每次 submitList 的插入/删除/移动/变更都会用
    // "list 下标"通知 RecyclerView,而真实 adapter 位置是 "list 下标 + headerCount",导致
    // RecyclerView 内部位置/动画记账错乱,是 "Inconsistency detected" 崩溃的已知诱因。
    // 无 header 的既有使用方(SearchAdapter/ChangeSourceAdapter/CoverAdapter/CacheAdapter/
    // BaseBooksAdapter 五处)headerCount=0,偏移量恒为 0,行为不变。
    //
    // 偏移逻辑抽成 companion 里的 offsetListUpdateCallback,而不是内联在这个 lazy 块里:
    // 本仓库没有 Robolectric,直接 new 一个 DiffRecyclerAdapter 子类会在构造期触碰
    // LayoutInflater.from(context),new AsyncListDiffer(...) 也会在类初始化时触碰
    // Looper.getMainLooper(),纯 JVM 单测环境下两者都会抛 "not mocked" 异常。抽出的函数
    // 不依赖任何 Android 运行时状态,可以在纯 JVM 单测里直接验证偏移算术,且是这里实际
    // 使用的同一份逻辑(而不是测试自建的另一份拷贝)。
    private val asyncListDiffer: AsyncListDiffer<ITEM> by lazy {
        val offsetCallback = offsetListUpdateCallback(
            headerCount = { getHeaderCount() },
            onInserted = { position, count -> notifyItemRangeInserted(position, count) },
            onRemoved = { position, count -> notifyItemRangeRemoved(position, count) },
            onMoved = { fromPosition, toPosition -> notifyItemMoved(fromPosition, toPosition) },
            onChanged = { position, count, payload -> notifyItemRangeChanged(position, count, payload) }
        )
        AsyncListDiffer(offsetCallback, AsyncDifferConfig.Builder(diffItemCallback).build()).apply {
            addListListener { _, _ ->
                onCurrentListChanged()
                if (keepScrollPosition) {
                    layoutManager?.onRestoreInstanceState(layoutState)
                    layoutState = null
                }
            }
        }
    }

    private var itemClickListener: ((holder: ItemViewHolder, item: ITEM) -> Unit)? = null
    private var itemLongClickListener: ((holder: ItemViewHolder, item: ITEM) -> Boolean)? = null

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var layoutState: Parcelable? = null

    var itemAnimation: ItemAnimation? = null

    abstract val diffItemCallback: DiffUtil.ItemCallback<ITEM>

    open val keepScrollPosition = false

    fun setOnItemClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Unit) {
        itemClickListener = listener
    }

    fun setOnItemLongClickListener(listener: (holder: ItemViewHolder, item: ITEM) -> Boolean) {
        itemLongClickListener = listener
    }

    fun bindToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.adapter = this
    }

    @Synchronized
    fun addHeaderView(header: ((parent: ViewGroup) -> ViewBinding)) {
        kotlin.runCatching {
            val index = headerItems.size()
            headerItems.put(TYPE_HEADER_VIEW + headerItems.size(), header)
            notifyItemInserted(index)
        }
    }

    @Synchronized
    fun removeHeaderView(header: ((parent: ViewGroup) -> ViewBinding)) {
        kotlin.runCatching {
            val index = headerItems.indexOfValue(header)
            if (index >= 0) {
                headerItems.remove(index)
                notifyItemRemoved(index)
            }
        }
    }

    fun getHeaderCount() = headerItems.size()

    fun setItems(items: List<ITEM>?) {
        kotlin.runCatching {
            if (keepScrollPosition) {
                layoutState = layoutManager?.onSaveInstanceState()
            }
            asyncListDiffer.submitList(items?.toMutableList())
        }
    }

    // position 是 list-space 下标(直接索引 currentList),notify 需补 headerCount 才是
    // 真实 adapter 位置——与 setItems/getItem 的换算方向一致
    fun setItem(position: Int, item: ITEM) {
        kotlin.runCatching {
            asyncListDiffer.currentList[position] = item
            notifyItemChanged(position + getHeaderCount())
        }
    }

    fun updateItem(item: ITEM) {
        kotlin.runCatching {
            val index = asyncListDiffer.currentList.indexOf(item)
            if (index >= 0) {
                asyncListDiffer.currentList[index] = item
                notifyItemChanged(index + getHeaderCount())
            }
        }
    }

    // 边界检查改用 getItems().size(list-space 容量),而非含 header 的 itemCount,
    // 否则 position 越过列表末尾也能通过校验,+headerCount 后 notify 位置会越出真实
    // itemCount 范围,反而更容易触发 RecyclerView "Inconsistency detected"
    fun updateItem(position: Int, payload: Any) {
        kotlin.runCatching {
            val size = getItems().size
            if (position in 0 until size) {
                notifyItemChanged(position + getHeaderCount(), payload)
            }
        }
    }

    fun updateItems(fromPosition: Int, toPosition: Int, payloads: Any) {
        kotlin.runCatching {
            val size = getItems().size
            if (fromPosition in 0 until size && toPosition in 0 until size) {
                notifyItemRangeChanged(
                    fromPosition + getHeaderCount(),
                    toPosition - fromPosition + 1,
                    payloads
                )
            }
        }
    }

    fun isEmpty() = asyncListDiffer.currentList.isEmpty()

    fun isNotEmpty() = asyncListDiffer.currentList.isNotEmpty()

    // position 是含 header 的 adapter/layout 位置,减去 headerCount 换算回底层 list 下标;
    // header 位置(position < headerCount)换算后为负,getOrNull 安全返回 null
    fun getItem(position: Int): ITEM? =
        asyncListDiffer.currentList.getOrNull(position - getHeaderCount())

    fun getItems(): List<ITEM> = asyncListDiffer.currentList

    /**
     * grid 模式下使用
     */
    protected open fun getSpanSize(viewType: Int, position: Int) = 1

    final override fun getItemCount() = getItems().size + getHeaderCount()

    private fun isHeader(position: Int) = position < getHeaderCount()

    final override fun getItemViewType(position: Int): Int {
        return if (isHeader(position)) TYPE_HEADER_VIEW + position else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        return if (viewType < TYPE_HEADER_VIEW + getHeaderCount()) {
            ItemViewHolder(headerItems.get(viewType).invoke(parent))
        } else {
            ItemViewHolder(getViewBinding(parent))
        }
    }

    protected abstract fun getViewBinding(parent: ViewGroup): VB

    final override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {}

    open fun onCurrentListChanged() {
        //可继承
    }

    @Suppress("UNCHECKED_CAST")
    final override fun onBindViewHolder(
        holder: ItemViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (isHeader(holder.layoutPosition)) return
        registerListener(holder, (holder.binding as VB))
        registerItemListener(holder)
        getItem(holder.layoutPosition)?.let {
            convert(holder, holder.binding, it, payloads)
        }
    }

    private fun registerItemListener(holder: ItemViewHolder) {
        if (itemClickListener != null) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    itemClickListener?.invoke(holder, it)
                }
            }
        }

        if (itemLongClickListener != null) {
            holder.itemView.onLongClick {
                getItem(holder.layoutPosition)?.let {
                    itemLongClickListener?.invoke(holder, it)
                }
            }
        }
    }

    override fun onViewAttachedToWindow(holder: ItemViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (!isHeader(holder.layoutPosition)) {
            addAnimation(holder)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val manager = recyclerView.layoutManager
        layoutManager = manager
        if (manager is GridLayoutManager) {
            manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return getSpanSize(getItemViewType(position), position)
                }
            }
        }
    }

    private fun addAnimation(holder: ItemViewHolder) {
        itemAnimation?.let {
            if (it.itemAnimEnabled) {
                if (!it.itemAnimFirstOnly || holder.layoutPosition > it.itemAnimStartPosition) {
                    startAnimation(holder, it)
                    it.itemAnimStartPosition = holder.layoutPosition
                }
            }
        }
    }

    protected open fun startAnimation(holder: ItemViewHolder, item: ItemAnimation) {
        item.itemAnimation?.let {
            for (anim in it.getAnimators(holder.itemView)) {
                anim.setDuration(item.itemAnimDuration).start()
                anim.interpolator = item.itemAnimInterpolator
            }
        }
    }

    /**
     * 如果使用了事件回调,回调里不要直接使用item,会出现不更新的问题,
     * 使用getItem(holder.layoutPosition)来获取item
     */
    abstract fun convert(
        holder: ItemViewHolder,
        binding: VB,
        item: ITEM,
        payloads: MutableList<Any>
    )

    /**
     * 注册事件
     */
    abstract fun registerListener(holder: ItemViewHolder, binding: VB)

    companion object {
        private const val TYPE_HEADER_VIEW = Int.MIN_VALUE

        // 纯函数,不触碰任何 Android 运行时状态,方便在无 Robolectric 的纯 JVM 单测里
        // 直接验证 header 偏移算术(见 DiffRecyclerAdapterHeaderOffsetTest)
        internal fun offsetListUpdateCallback(
            headerCount: () -> Int,
            onInserted: (position: Int, count: Int) -> Unit,
            onRemoved: (position: Int, count: Int) -> Unit,
            onMoved: (fromPosition: Int, toPosition: Int) -> Unit,
            onChanged: (position: Int, count: Int, payload: Any?) -> Unit
        ): ListUpdateCallback = object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) =
                onInserted(position + headerCount(), count)

            override fun onRemoved(position: Int, count: Int) =
                onRemoved(position + headerCount(), count)

            override fun onMoved(fromPosition: Int, toPosition: Int) =
                onMoved(fromPosition + headerCount(), toPosition + headerCount())

            override fun onChanged(position: Int, count: Int, payload: Any?) =
                onChanged(position + headerCount(), count, payload)
        }
    }

}