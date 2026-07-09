package io.legado.app.ui.book.source.manage

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.base.adapter.SimpleSelectableAdapter
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ItemBookSourceBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.Debug
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.dpToPx
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible
import java.util.Collections


class BookSourceAdapter(
    context: Context,
    private val callBack: CallBack,
    private val recyclerView: RecyclerView
) : RecyclerAdapter<BookSourcePart, ItemBookSourceBinding>(context),
    ItemTouchCallback.Callback,
    SimpleSelectableAdapter<BookSourcePart> {

    override val selectedKeys = linkedSetOf<BookSourcePart>()
    private val finalMessageRegex = Regex("成功|失败")
    private val handler = buildMainHandler()
    var showSourceHost = false

    override fun onSelectionChanged() {
        callBack.upCountView()
    }

    val diffItemCallback = object : DiffUtil.ItemCallback<BookSourcePart>() {

        override fun areItemsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
            return oldItem.bookSourceUrl == newItem.bookSourceUrl
        }

        override fun areContentsTheSame(oldItem: BookSourcePart, newItem: BookSourcePart): Boolean {
            return oldItem.bookSourceName == newItem.bookSourceName
                    && oldItem.bookSourceGroup == newItem.bookSourceGroup
                    && oldItem.enabled == newItem.enabled
                    && oldItem.enabledExplore == newItem.enabledExplore
                    && oldItem.hasExploreUrl == newItem.hasExploreUrl
                    && oldItem.hasJs == newItem.hasJs
        }

        override fun getChangePayload(oldItem: BookSourcePart, newItem: BookSourcePart): Any? {
            val payload = Bundle()
            if (oldItem.bookSourceName != newItem.bookSourceName
                || oldItem.bookSourceGroup != newItem.bookSourceGroup
            ) {
                payload.putBoolean("upName", true)
            }
            if (oldItem.enabled != newItem.enabled) {
                payload.putBoolean("enabled", newItem.enabled)
            }
            if (oldItem.enabledExplore != newItem.enabledExplore ||
                oldItem.hasExploreUrl != newItem.hasExploreUrl
            ) {
                payload.putBoolean("upExplore", true)
            }
            if (oldItem.hasJs != newItem.hasJs) {
                payload.putBoolean("upJs", true)
            }
            if (payload.isEmpty) {
                return null
            }
            return payload
        }

    }

    override fun getViewBinding(parent: ViewGroup): ItemBookSourceBinding {
        return ItemBookSourceBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemBookSourceBinding,
        item: BookSourcePart,
        payloads: MutableList<Any>
    ) {
        binding.run {
            // 卡底色由换肤引擎按布局 skin_background 施加;沉浸式同样呈卡片
            if (payloads.isEmpty()) {
                cbBookSource.text = item.getDisPlayNameGroup()
                swtEnabled.isChecked = item.enabled
                cbBookSource.isChecked = isSelected(item)
                upSelectStroke(binding, item)
                upCheckSourceMessage(binding, item)
                upShowExplore(ivExplore, item)
                tvJsBadge.visible(item.hasJs)
                upSourceHost(binding, holder.layoutPosition)
            } else {
                for (i in payloads.indices) {
                    val bundle = payloads[i] as Bundle
                    bundle.keySet().forEach {
                        when (it) {
                            "enabled" -> swtEnabled.isChecked = bundle.getBoolean("enabled")
                            "upName" -> cbBookSource.text = item.getDisPlayNameGroup()
                            "upExplore" -> upShowExplore(ivExplore, item)
                            "upJs" -> tvJsBadge.visible(item.hasJs)
                            "selected" -> {
                                cbBookSource.isChecked = isSelected(item)
                                upSelectStroke(binding, item)
                            }
                            "checkSourceMessage" -> upCheckSourceMessage(binding, item)
                            "upSourceHost" -> upSourceHost(binding, holder.layoutPosition)
                        }
                    }
                }
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemBookSourceBinding) {
        binding.apply {
            swtEnabled.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    it.enabled = checked
                    callBack.enable(checked, it)
                }
            }
            cbBookSource.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    setSelected(it, checked)
                    upSelectStroke(binding, it)
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
            contentLayout.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    val nowSelected = !isSelected(it)
                    setSelected(it, nowSelected)
                    cbBookSource.isChecked = nowSelected
                    upSelectStroke(binding, it)
                    callBack.upCountView()
                }
            }
        }
    }

    override fun onCurrentListChanged() {
        callBack.upCountView()
        recyclerView.doOnLayout {
            handler.post {
                notifyItemRangeChanged(0, itemCount, bundleOf("upSourceHost" to null))
            }
        }
    }

    private fun showMenu(view: View, position: Int) {
        val source = getItem(position) ?: return
        val items = buildList {
            if (callBack.sort == BookSourceSort.Default) {
                add(SelectItem(context.getString(R.string.to_top), "top"))
                add(SelectItem(context.getString(R.string.to_bottom), "bottom"))
            }
            if (source.hasLoginUrl) {
                add(SelectItem(context.getString(R.string.login), "login"))
            }
            add(SelectItem(context.getString(R.string.search), "search"))
            add(SelectItem(context.getString(R.string.debug), "debug"))
            if (source.hasExploreUrl) {
                add(
                    SelectItem(
                        context.getString(
                            if (source.enabledExplore) R.string.disable_explore else R.string.enable_explore
                        ),
                        "toggleExplore"
                    )
                )
            }
            add(SelectItem(context.getString(R.string.delete), "delete"))
        }
        PopupAction(context).apply {
            setVertical(true)
            setDangerValues(setOf("delete"))
            setItems(items)
            onActionClick = { action ->
                when (action) {
                    "top" -> callBack.toTop(source)
                    "bottom" -> callBack.toBottom(source)
                    "login" -> context.startActivity<SourceLoginActivity> {
                        putExtra("type", "bookSource")
                        putExtra("key", source.bookSourceUrl)
                    }

                    "search" -> callBack.searchBook(source)
                    "debug" -> callBack.debug(source)
                    "delete" -> {
                        callBack.del(source)
                        setSelected(source, false)
                    }

                    "toggleExplore" -> callBack.enableExplore(!source.enabledExplore, source)
                }
                dismiss()
            }
            showAsDropDown(view, 0, 4.dpToPx())
        }
    }

    private fun upSelectStroke(binding: ItemBookSourceBinding, source: BookSourcePart) {
        binding.rootCard.strokeColor = context.accentColor
        binding.rootCard.strokeWidth = if (isSelected(source)) 2.dpToPx() else 0
    }

    private fun upShowExplore(iv: ImageView, source: BookSourcePart) {
        when {
            !source.hasExploreUrl -> {
                iv.invisible()
            }

            source.enabledExplore -> {
                iv.setColorFilter(Color.GREEN)
                iv.visible()
                iv.contentDescription = context.getString(R.string.tag_explore_enabled)
            }

            else -> {
                iv.setColorFilter(Color.RED)
                iv.visible()
                iv.contentDescription = context.getString(R.string.tag_explore_disabled)
            }
        }
    }

    private fun upCheckSourceMessage(
        binding: ItemBookSourceBinding,
        item: BookSourcePart
    ) = binding.run {
        val msg = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
        ivDebugText.text = msg
        val isEmpty = msg.isEmpty()
        var isFinalMessage = msg.contains(finalMessageRegex)
        if (!Debug.isChecking && !isFinalMessage) {
            Debug.updateFinalMessage(item.bookSourceUrl, "校验失败")
            ivDebugText.text = Debug.debugMessageMap[item.bookSourceUrl] ?: ""
            isFinalMessage = true
        }
        ivDebugText.visibility =
            if (!isEmpty) View.VISIBLE else View.GONE
        ivProgressBar.visibility =
            if (isFinalMessage || isEmpty || !Debug.isChecking) View.GONE else View.VISIBLE
    }

    private fun upSourceHost(binding: ItemBookSourceBinding, position: Int) = binding.run {
        if (showSourceHost && isItemHeader(position)) {
            tvHostText.text = getHeaderText(position)
            tvHostText.visible()
        } else {
            tvHostText.gone()
        }
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

    fun getHeaderText(position: Int): String {
        val source = getItem(position)!!
        return callBack.getSourceHost(source.bookSourceUrl)
    }

    fun isItemHeader(position: Int): Boolean {
        if (position == 0) return true
        val lastHost = getHeaderText(position - 1)
        val curHost = getHeaderText(position)
        return lastHost != curHost
    }

    override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
        val srcItem = getItem(srcPosition)
        val targetItem = getItem(targetPosition)
        if (srcItem != null && targetItem != null) {
            val srcOrder = srcItem.customOrder
            srcItem.customOrder = targetItem.customOrder
            targetItem.customOrder = srcOrder
            movedItems.add(srcItem)
            movedItems.add(targetItem)
        }
        swapItem(srcPosition, targetPosition)
        return true
    }

    private val movedItems = hashSetOf<BookSourcePart>()

    override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (movedItems.isNotEmpty()) {
            val sortNumberSet = hashSetOf<Int>()
            movedItems.forEach {
                sortNumberSet.add(it.customOrder)
            }
            if (movedItems.size > sortNumberSet.size) {
                callBack.upOrder(getItems().mapIndexed { index, bookSourcePart ->
                    bookSourcePart.customOrder = if (callBack.sortAscending) index else -index
                    bookSourcePart
                })
            } else {
                callBack.upOrder(movedItems.toList())
            }
            movedItems.clear()
        }
    }

    val dragSelectCallback: DragSelectTouchHelper.Callback =
        object : DragSelectTouchHelper.AdvanceCallback<BookSourcePart>(Mode.ToggleAndReverse) {
            override fun currentSelectedId(): MutableSet<BookSourcePart> {
                return selectedKeys
            }

            override fun getItemId(position: Int): BookSourcePart {
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
        val sort: BookSourceSort
        val sortAscending: Boolean
        fun del(bookSource: BookSourcePart)
        fun edit(bookSource: BookSourcePart)
        fun toTop(bookSource: BookSourcePart)
        fun toBottom(bookSource: BookSourcePart)
        fun searchBook(bookSource: BookSourcePart)
        fun debug(bookSource: BookSourcePart)
        fun upOrder(items: List<BookSourcePart>)
        fun enable(enable: Boolean, bookSource: BookSourcePart)
        fun enableExplore(enable: Boolean, bookSource: BookSourcePart)
        fun upCountView()
        fun getSourceHost(origin: String): String
    }
}
