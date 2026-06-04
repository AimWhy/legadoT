package io.legado.app.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.FragmentChapterListBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.appBarBackgroundIsLight
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.AudioCache
import io.legado.app.model.AudioCacheStateChanged
import io.legado.app.ui.widget.recycler.UpLinearLayoutManager
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private val mLayoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { ChapterListAdapter(requireContext(), this) }
    private var durChapterIndex = 0
    private var audioCacheStateReady = false
    private val pendingAudioCacheChanges = linkedMapOf<Int, Boolean>()
    private var initBookJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        viewModel.chapterListCallBack = this@ChapterListFragment
        // 沉浸式时底栏透明，露出页面背景，与顶栏/底部导航一致；前景色按可见背景判断明暗
        val immersive = AppConfig.isTransparentActionBar
        val btc = requireContext().getPrimaryTextColor(
            appBarBackgroundIsLight(
                transparentActionBar = immersive,
                barBackgroundColor = bottomBackground,
                contentBackgroundColor = requireContext().backgroundColor
            )
        )
        llChapterBaseInfo.setBackgroundColor(if (immersive) Color.TRANSPARENT else bottomBackground)
        // 透明时去掉 elevation，避免栏看不见却仍在内容上投出阴影线（与 TitleBar 一致）
        llChapterBaseInfo.elevation = if (immersive) 0f else 5f.dpToPx()
        tvCurrentChapterInfo.setTextColor(btc)
        ivChapterTop.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        ivChapterBottom.setColorFilter(btc, PorterDuff.Mode.SRC_IN)
        initRecyclerView()
        initView()
        viewModel.bookData.observe(this@ChapterListFragment) {
            initBook(it)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() = binding.run {
        ivChapterTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        ivChapterBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        tvCurrentChapterInfo.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        }
        binding.llChapterBaseInfo.applyNavigationBarPadding()
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(book: Book) {
        initBookJob?.cancel()
        initBookJob = lifecycleScope.launch {
            durChapterIndex = book.durChapterIndex
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})"

            adapter.cacheFileNames.clear()
            adapter.cachedChapterIndexes.clear()
            audioCacheStateReady = !book.isAudio
            pendingAudioCacheChanges.clear()

            adapter.setAllItems(queryChapterList(null))

            val (cacheFileNames, cachedChapterIndexes) = queryCacheState(book)
            adapter.cacheFileNames.addAll(cacheFileNames)
            adapter.cachedChapterIndexes.addAll(cachedChapterIndexes)
            pendingAudioCacheChanges.forEach { (chapterIndex, cached) ->
                if (cached) {
                    adapter.cachedChapterIndexes.add(chapterIndex)
                } else {
                    adapter.cachedChapterIndexes.remove(chapterIndex)
                }
            }
            pendingAudioCacheChanges.clear()
            audioCacheStateReady = true
            adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
        }
    }

    private suspend fun queryCacheState(book: Book): Pair<Set<String>, Set<Int>> {
        return withContext(IO) {
            if (book.isAudio) {
                Pair(emptySet(), AudioCache.listCachedChapterIndexes(book.bookUrl))
            } else {
                Pair(BookHelp.getChapterFiles(book).toSet(), emptySet())
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.bookData.value?.bookUrl?.let { bookUrl ->
                if (viewModel.bookData.value?.isAudio == true) {
                    return@observeEvent
                }
                if (book.bookUrl == bookUrl) {
                    adapter.cacheFileNames.add(chapter.getFileName())
                    if (viewModel.searchKey.isNullOrEmpty()) {
                        adapter.notifyItemChanged(chapter.index, true)
                    } else {
                        adapter.getItems().forEachIndexed { index, bookChapter ->
                            if (bookChapter.index == chapter.index) {
                                adapter.notifyItemChanged(index, true)
                            }
                        }
                    }
                }
            }
        }
        observeEvent<AudioCacheStateChanged>(EventBus.AUDIO_CACHE_CHANGED) { event ->
            val currentBook = viewModel.bookData.value ?: return@observeEvent
            if (!currentBook.isAudio || currentBook.bookUrl != event.bookUrl) return@observeEvent
            if (!audioCacheStateReady) {
                pendingAudioCacheChanges[event.chapterIndex] = event.cached
                return@observeEvent
            }
            updateAudioChapterCacheState(event.chapterIndex, event.cached)
        }
    }

    override fun upChapterList(searchKey: String?) {
        lifecycleScope.launch {
            adapter.setAllItems(queryChapterList(searchKey))
        }
    }

    private suspend fun queryChapterList(searchKey: String?): List<BookChapter> {
        val end = (book?.simulatedTotalChapterNum() ?: Int.MAX_VALUE) - 1
        return viewModel.queryChapterList(searchKey, end)
    }

    private fun updateAudioChapterCacheState(chapterIndex: Int, cached: Boolean) {
        if (cached) {
            adapter.cachedChapterIndexes.add(chapterIndex)
        } else {
            adapter.cachedChapterIndexes.remove(chapterIndex)
        }
        if (viewModel.searchKey.isNullOrEmpty()) {
            if (chapterIndex in 0 until adapter.itemCount) {
                adapter.notifyItemChanged(chapterIndex, true)
            }
            return
        }
        adapter.getItems().forEachIndexed { index, bookChapter ->
            if (bookChapter.index == chapterIndex) {
                adapter.notifyItemChanged(index, true)
                return
            }
        }
    }

    private var pendingScrollAnchor: Int? = null
    /** 锚定条目的 index（而非列表位置），用于位置变化后查找 */
    private var pendingScrollItemIndex: Int? = null

    override fun onListChanged() {
        if (pendingScrollAnchor != null) {
            // 分卷切换中，跳过自动滚动，由 onItemsUpdated 恢复位置
            return
        }
        lifecycleScope.launch {
            var scrollPos = 0
            withContext(Default) {
                adapter.getItems().forEachIndexed { index, bookChapter ->
                    if (bookChapter.index >= durChapterIndex) {
                        return@withContext
                    }
                    scrollPos = index
                }
            }
            mLayoutManager.scrollToPositionWithOffset(scrollPos, 0)
            adapter.upDisplayTitles(scrollPos)
        }
    }

    override fun onVolumeToggled(volumeIndex: Int) {
        val pos = mLayoutManager.findFirstVisibleItemPosition()
        pendingScrollAnchor = pos
        pendingScrollItemIndex = adapter.getItems().getOrNull(pos)?.index
    }

    override fun onItemsUpdated() {
        val anchorItemIdx = pendingScrollItemIndex
        pendingScrollAnchor = null
        pendingScrollItemIndex = null
        if (anchorItemIdx == null) return
        binding.recyclerView.post {
            val newPos = adapter.getItems().indexOfFirst { it.index == anchorItemIdx }
            if (newPos >= 0) {
                mLayoutManager.scrollToPositionWithOffset(newPos, 0)
            }
            adapter.upDisplayTitles(newPos.coerceAtLeast(0))
        }
    }

    override fun clearDisplayTitle() {
        adapter.clearDisplayTitle()
        adapter.upDisplayTitles(mLayoutManager.findFirstVisibleItemPosition())
    }

    override fun upAdapter() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    override val scope: CoroutineScope
        get() = lifecycleScope

    override val book: Book?
        get() = viewModel.bookData.value

    override val isLocalBook: Boolean
        get() = viewModel.bookData.value?.isLocal == true

    override val isAudioBook: Boolean
        get() = viewModel.bookData.value?.isAudio == true

    override val isAudioCacheStateReady: Boolean
        get() = audioCacheStateReady

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

    override fun openChapter(bookChapter: BookChapter) {
        activity?.run {
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }

}
