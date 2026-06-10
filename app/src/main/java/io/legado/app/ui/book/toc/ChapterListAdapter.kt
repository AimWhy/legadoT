package io.legado.app.ui.book.toc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.core.view.updatePaddingRelative
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
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ChapterListAdapter(context: Context, val callback: Callback) :
    DiffRecyclerAdapter<TocListItem, ItemChapterListBinding>(context) {

    val cacheFileNames = hashSetOf<String>()
    val cachedChapterIndexes = hashSetOf<Int>()
    private val displayTitleMap = ConcurrentHashMap<String, String>()
    private val handler = Handler(Looper.getMainLooper())

    override val diffItemCallback: DiffUtil.ItemCallback<TocListItem>
        get() = object : DiffUtil.ItemCallback<TocListItem>() {

            override fun areItemsTheSame(oldItem: TocListItem, newItem: TocListItem): Boolean {
                return oldItem.key == newItem.key
            }

            override fun areContentsTheSame(oldItem: TocListItem, newItem: TocListItem): Boolean {
                if (oldItem::class != newItem::class) return false
                if (oldItem.depth != newItem.depth) return false
                if (!sameChapterContent(oldItem.chapter, newItem.chapter)) return false
                return when {
                    oldItem is TocListItem.Volume && newItem is TocListItem.Volume ->
                        oldItem.collapsed == newItem.collapsed &&
                                oldItem.chapterCount == newItem.chapterCount &&
                                oldItem.matchedCount == newItem.matchedCount &&
                                oldItem.matchedSelf == newItem.matchedSelf &&
                                oldItem.containsDurChapter == newItem.containsDurChapter

                    oldItem is TocListItem.Chapter && newItem is TocListItem.Chapter ->
                        oldItem.parentVolumeIndex == newItem.parentVolumeIndex

                    else -> false
                }
            }
        }

    private fun sameChapterContent(oldItem: BookChapter, newItem: BookChapter): Boolean {
        return oldItem.bookUrl == newItem.bookUrl &&
                oldItem.url == newItem.url &&
                oldItem.isVip == newItem.isVip &&
                oldItem.isPay == newItem.isPay &&
                oldItem.title == newItem.title &&
                oldItem.tag == newItem.tag &&
                oldItem.wordCount == newItem.wordCount &&
                oldItem.isVolume == newItem.isVolume
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
                    val chapter = items[i].chapter
                    if (displayTitleMap[chapter.title] == null) {
                        ensureActive()
                        val displayTitle = chapter.getDisplayTitle(replaceRules, useReplace)
                        ensureActive()
                        displayTitleMap[chapter.title] = displayTitle
                        handler.post {
                            notifyItemChanged(i, true)
                        }
                    }
                }
            }
            launch {
                for (i in startIndex downTo 0) {
                    val chapter = items[i].chapter
                    if (displayTitleMap[chapter.title] == null) {
                        ensureActive()
                        val displayTitle = chapter.getDisplayTitle(replaceRules, useReplace)
                        ensureActive()
                        displayTitleMap[chapter.title] = displayTitle
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

    override fun getViewBinding(parent: ViewGroup): ItemChapterListBinding {
        return ItemChapterListBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChapterListBinding,
        item: TocListItem,
        payloads: MutableList<Any>
    ) {
        binding.run {
            val chapter = item.chapter
            val isVolume = item is TocListItem.Volume
            val isDur = callback.durChapterIndex() == chapter.index
            val cached = callback.isLocalBook ||
                    isVolume ||
                    if (callback.isAudioBook) {
                        !callback.isAudioCacheStateReady || cachedChapterIndexes.contains(chapter.index)
                    } else {
                        cacheFileNames.contains(chapter.getFileName())
                    }
            tvChapterItem.updatePaddingRelative(start = 12.dpToPx() + (item.depth * 10).dpToPx())
            if (payloads.isEmpty()) {
                if (isDur) {
                    tvChapterName.setTextColor(context.accentColor)
                } else {
                    tvChapterName.setTextColor(context.getCompatColor(R.color.primaryText))
                }
                tvChapterName.text = getDisplayTitle(chapter)
                if (isVolume) {
                    tvChapterItem.setBackgroundColor(context.getCompatColor(R.color.btn_bg_press))
                } else {
                    tvChapterItem.background =
                        ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)
                }

                if (item is TocListItem.Volume) {
                    tvWordCount.gone()
                    tvTag.text = volumeSummary(item)
                    tvTag.visible()
                } else {
                    if (!chapter.tag.isNullOrEmpty()) {
                        tvTag.text = chapter.tag
                        tvTag.visible()
                    } else {
                        tvTag.gone()
                    }
                    if (AppConfig.tocCountWords && !chapter.wordCount.isNullOrEmpty()) {
                        tvWordCount.text = chapter.wordCount
                        tvWordCount.visible()
                    } else {
                        tvWordCount.gone()
                    }
                }

                if (chapter.isVip && !chapter.isPay && !isVolume) {
                    ivLocked.visible()
                } else {
                    ivLocked.gone()
                }

                upHasCache(binding, isDur, cached)
            } else {
                tvChapterName.text = getDisplayTitle(chapter)
                upHasCache(binding, isDur, cached)
            }
            if (item is TocListItem.Volume) {
                ivVolumeArrow.visible()
                ivVolumeArrow.setImageResource(
                    if (item.collapsed) R.drawable.ic_arrow_right else R.drawable.ic_expand_more
                )
            } else {
                ivVolumeArrow.gone()
            }
        }
    }

    private fun volumeSummary(item: TocListItem.Volume): String {
        return when {
            item.matchedSelf && item.matchedCount == 0 -> "分卷匹配"
            item.matchedCount != null -> "匹配 ${item.matchedCount}/${item.chapterCount}"
            item.collapsed -> "已折叠 ${item.chapterCount} 章"
            else -> "共 ${item.chapterCount} 章"
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemChapterListBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                when (item) {
                    is TocListItem.Volume -> callback.onVolumeToggled(item.chapter.index)
                    is TocListItem.Chapter -> callback.openChapter(item.chapter)
                }
            }
        }
        holder.itemView.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                context.longToastOnUi(getDisplayTitle(item.chapter))
            }
            true
        }
    }

    fun findVisiblePositionByChapterIndex(chapterIndex: Int): Int {
        return getItems().indexOfFirst {
            it is TocListItem.Chapter && it.chapter.index == chapterIndex
        }
    }

    fun findVisiblePositionByItemKey(key: String): Int {
        return getItems().indexOfFirst { it.key == key }
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
