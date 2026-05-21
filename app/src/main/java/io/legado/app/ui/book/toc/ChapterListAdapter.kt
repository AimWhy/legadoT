package io.legado.app.ui.book.toc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ItemChapterListBinding
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ChapterListAdapter(context: Context, val callback: Callback) :
    DiffRecyclerAdapter<BookChapter, ItemChapterListBinding>(context) {

    val cacheFileNames = hashSetOf<String>()
    val cachedChapterIndexes = hashSetOf<Int>()
    private val displayTitleMap = ConcurrentHashMap<String, String>()
    private val handler = Handler(Looper.getMainLooper())

    /** 完整未过滤的章节目录 */
    private var allItems: List<BookChapter> = emptyList()
    /** 已折叠的分卷 index 集合 */
    val collapsedVolumes = mutableSetOf<Int>()

    override val diffItemCallback: DiffUtil.ItemCallback<BookChapter>
        get() = object : DiffUtil.ItemCallback<BookChapter>() {

            override fun areItemsTheSame(
                oldItem: BookChapter,
                newItem: BookChapter
            ): Boolean {
                return oldItem.index == newItem.index
            }

            override fun areContentsTheSame(
                oldItem: BookChapter,
                newItem: BookChapter
            ): Boolean {
                return oldItem.bookUrl == newItem.bookUrl
                        && oldItem.url == newItem.url
                        && oldItem.isVip == newItem.isVip
                        && oldItem.isPay == newItem.isPay
                        && oldItem.title == newItem.title
                        && oldItem.tag == newItem.tag
                        && oldItem.wordCount == newItem.wordCount
                        && oldItem.isVolume == newItem.isVolume
            }

        }

    private var upDisplayTileJob: Coroutine<*>? = null

    override fun onCurrentListChanged() {
        super.onCurrentListChanged()
        callback.onListChanged()
        // 通知 Fragment 恢复滚动位置
        handler.post { callback.onItemsUpdated() }
    }

    fun clearDisplayTitle() {
        upDisplayTileJob?.cancel()
        displayTitleMap.clear()
    }

    fun upDisplayTitles(startIndex: Int) {
        upDisplayTileJob?.cancel()
        upDisplayTileJob = Coroutine.async(callback.scope) {
            val book = callback.book ?: return@async
            val replaceRules = ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
            val useReplace = AppConfig.tocUiUseReplace && book.getUseReplaceRule()
            val items = getItems()
            launch {
                for (i in startIndex until items.size) {
                    val item = items[i]
                    if (displayTitleMap[item.title] == null) {
                        ensureActive()
                        val displayTitle = item.getDisplayTitle(replaceRules, useReplace)
                        ensureActive()
                        displayTitleMap[item.title] = displayTitle
                        handler.post {
                            notifyItemChanged(i, true)
                        }
                    }
                }
            }
            launch {
                for (i in startIndex downTo 0) {
                    val item = items[i]
                    if (displayTitleMap[item.title] == null) {
                        ensureActive()
                        val displayTitle = item.getDisplayTitle(replaceRules, useReplace)
                        ensureActive()
                        displayTitleMap[item.title] = displayTitle
                        handler.post {
                            notifyItemChanged(i, true)
                        }
                    }
                }
            }
        }
    }

    private fun getDisplayTitle(chapter: BookChapter): String {
        return displayTitleMap[chapter.title] ?: chapter.title
    }

    fun setAllItems(items: List<BookChapter>) {
        allItems = items
        collapsedVolumes.clear()
        val durIdx = callback.durChapterIndex()
        var currentVolumeIndex: Int? = null
        for (item in allItems) {
            if (item.isVolume && item.index <= durIdx) {
                currentVolumeIndex = item.index
            }
        }
        allItems.filter { it.isVolume }.forEach { volume ->
            if (volume.index != currentVolumeIndex) {
                collapsedVolumes.add(volume.index)
            }
        }
        refreshVisibleItems()
    }

    fun refreshVisibleItems() {
        val visible = computeVisibleItems()
        super.setItems(visible)
    }

    private fun computeVisibleItems(): List<BookChapter> {
        val result = mutableListOf<BookChapter>()
        var skipping = false
        for (item in allItems) {
            if (item.isVolume) {
                result.add(item)
                skipping = collapsedVolumes.contains(item.index)
            } else if (!skipping) {
                result.add(item)
            }
        }
        return result
    }

    override fun getViewBinding(parent: ViewGroup): ItemChapterListBinding {
        return ItemChapterListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChapterListBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val isDur = callback.durChapterIndex() == item.index
            val cached = callback.isLocalBook
                    || item.isVolume
                    || if (callback.isAudioBook) {
                        !callback.isAudioCacheStateReady || cachedChapterIndexes.contains(item.index)
                    } else {
                        cacheFileNames.contains(item.getFileName())
                    }
            if (payloads.isEmpty()) {
                if (isDur) {
                    tvChapterName.setTextColor(context.accentColor)
                } else {
                    tvChapterName.setTextColor(context.getCompatColor(R.color.primaryText))
                }
                tvChapterName.text = getDisplayTitle(item)
                if (item.isVolume) {
                    tvChapterItem.setBackgroundColor(context.getCompatColor(R.color.btn_bg_press))
                } else {
                    tvChapterItem.background =
                        ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)
                }

                if (!item.tag.isNullOrEmpty() && !item.isVolume) {
                    tvTag.text = item.tag
                    tvTag.visible()
                } else {
                    tvTag.gone()
                }
                if (AppConfig.tocCountWords && !item.wordCount.isNullOrEmpty() && !item.isVolume) {
                    tvWordCount.text = item.wordCount
                    tvWordCount.visible()
                } else {
                    tvWordCount.gone()
                }

                if (item.isVip && !item.isPay) {
                    ivLocked.visible()
                } else {
                    ivLocked.gone()
                }

                upHasCache(binding, isDur, cached)
            } else {
                tvChapterName.text = getDisplayTitle(item)
                upHasCache(binding, isDur, cached)
            }
            if (item.isVolume) {
                ivVolumeArrow.visible()
                ivVolumeArrow.setImageResource(
                    if (collapsedVolumes.contains(item.index))
                        R.drawable.ic_arrow_right
                    else
                        R.drawable.ic_expand_more
                )
            } else {
                ivVolumeArrow.gone()
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemChapterListBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.isVolume) {
                    if (collapsedVolumes.contains(item.index)) {
                        collapsedVolumes.remove(item.index)
                    } else {
                        collapsedVolumes.add(item.index)
                    }
                    notifyItemChanged(holder.layoutPosition)
                    callback.onVolumeToggled(item.index)
                    refreshVisibleItems()
                } else {
                    callback.openChapter(item)
                }
            }
        }
        holder.itemView.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                context.longToastOnUi(getDisplayTitle(item))
            }
            true
        }
    }

    private fun upHasCache(binding: ItemChapterListBinding, isDur: Boolean, cached: Boolean) =
        binding.apply {
            ivChecked.setImageResource(R.drawable.ic_outline_cloud_24)
            ivChecked.visible(!cached)
            if (isDur) {
                ivChecked.setImageResource(R.drawable.ic_check)
                ivChecked.visible()
            }
        }

    interface Callback {
        val scope: CoroutineScope
        val book: Book?
        val isLocalBook: Boolean
        val isAudioBook: Boolean
        val isAudioCacheStateReady: Boolean
        fun openChapter(bookChapter: BookChapter)
        fun durChapterIndex(): Int
        fun onListChanged()
        fun onVolumeToggled(volumeIndex: Int)
        fun onItemsUpdated()
    }

}
