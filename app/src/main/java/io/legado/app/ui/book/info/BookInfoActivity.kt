package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.Window
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.platform.MaterialContainerTransform
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.databinding.DialogBookAutoTaskBinding
import io.legado.app.databinding.ItemBookInfoHeaderBinding
import io.legado.app.databinding.ItemBookInfoTocHeaderBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.motion.MotionTokens
import io.legado.app.help.motion.PressSpringEffect
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.model.SourceCallBack
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.ChapterListAdapter
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.book.toc.TocListItem
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.applyAmbientBackground
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

class BookInfoActivity :
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback {

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            readFromChapter(it.first, it.second, it.third)
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.upBook(intent)
        when (it.resultCode) {
            RESULT_OK -> {
                viewModel.inBookshelf = true
                upTvBookshelf()
            }

            RESULT_DELETED -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_CANCELED) {
            return@registerForActivityResult
        }
        book?.let { book ->
            viewModel.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            viewModel.refreshBook(book)
        }
    }
    private var chapterChanged = false
    private val waitDialog by lazy { WaitDialog(this) }
    private var editMenuItem: MenuItem? = null
    private var menuCustomBtn: MenuItem? = null
    private val book get() = viewModel.getBook(false)

    // N3a toc-listify: 详情页(portrait)内容区从"NestedScroll + 手搓预览"换成 RecyclerView
    // 承载完整目录,复用目录页既有的 ChapterListAdapter,不新建章节行布局/适配器。
    // headerBinding/tocHeaderBinding 可能晚于 viewModel 数据就绪才 inflate(addHeaderView 异步),
    // 故 bindInfoHeader/upTocHeader 内都做"回填当前已有数据"处理,showBook/upChapterList 侧也留 null-safe 更新。
    private var headerBinding: ItemBookInfoHeaderBinding? = null
    private var tocHeaderBinding: ItemBookInfoTocHeaderBinding? = null
    private var tocReversed = true   // 默认倒序(最新章在前)
    private var fullChapters: List<BookChapter> = emptyList()
    private val chapterAdapter by lazy { ChapterListAdapter(this, chapterCallback) }

    private val chapterCallback = object : ChapterListAdapter.Callback {
        override val scope get() = lifecycleScope
        override val book get() = viewModel.getBook(false)
        override val isLocalBook get() = viewModel.getBook(false)?.isLocal == true
        override val isAudioBook get() = viewModel.getBook(false)?.isAudio == true
        override val isAudioCacheStateReady get() = true
        override fun openChapter(bookChapter: BookChapter) {
            readFromChapter(bookChapter.index)
        }

        override fun durChapterIndex() = viewModel.getBook(false)?.durChapterIndex ?: 0
        override fun onListChanged() {}
        override fun onVolumeToggled(volumeIndex: Int) {}
        override fun onItemsUpdated() {}
    }

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (MotionTokens.enabled) {
            window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
            val transform = MaterialContainerTransform(this, true).apply {
                addTarget(R.id.iv_cover)
                duration = 320L
                fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
            }
            window.sharedElementEnterTransition = transform
            window.sharedElementReturnTransition = MaterialContainerTransform(this, false).apply {
                addTarget(R.id.iv_cover)
                duration = 280L
            }
        }
        super.onCreate(savedInstanceState)
    }

    @SuppressLint("PrivateResource")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.ivCover.transitionName =
            "book_cover_" + intent.getStringExtra("name").orEmpty() +
            intent.getStringExtra("author").orEmpty()
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.flAction.applyNavigationBarPadding()
        // tv_intro 在 land 仍是 binding 自己的字段(portrait 侧已迁入 header,在 bindInfoHeader 内单独设置)
        binding.tvIntro?.revealOnFocusHint = false
        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        // CollapsingToolbarLayout 构造器无条件挂 inset 监听且 onWindowInsetChanged 恒
        // consumeSystemWindowInsets()(1.13.0 字节码实证)——其子级(tool_bar/ll_header)的
        // applyStatusBarPadding 永远收到被吞掉的 insets。让位必须在消费点之前:root 监听取值直接下发。
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            val statusBarTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.toolBar.updatePadding(top = statusBarTop)
            binding.llHeader?.updatePadding(top = statusBarTop)
            windowInsets
        }
        binding.appBar?.addOnOffsetChangedListener { appBar, verticalOffset ->
            val range = appBar.totalScrollRange.takeIf { it > 0 } ?: return@addOnOffsetChangedListener
            val ratio = -verticalOffset.toFloat() / range
            binding.llHeader?.alpha = (1f - ratio * 1.4f).coerceIn(0f, 1f)
            binding.tvToolbarTitle.alpha = ((ratio - 0.6f) / 0.4f).coerceIn(0f, 1f)
            binding.refreshLayout.isEnabled = verticalOffset == 0
        }
        // N3a toc-listify: portrait 内容区列表化,仅在 recyclerView 非空(即 portrait)时装配;
        // land 仍是纯 ScrollView + 手搓预览(upTocPreview),recyclerView 为 null,不受影响。
        binding.recyclerView?.let { rv ->
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = chapterAdapter
            chapterAdapter.addHeaderView { parent ->
                ItemBookInfoHeaderBinding.inflate(layoutInflater, parent, false).also {
                    headerBinding = it
                    bindInfoHeader(it)
                }
            }
            chapterAdapter.addHeaderView { parent ->
                ItemBookInfoTocHeaderBinding.inflate(layoutInflater, parent, false).also {
                    tocHeaderBinding = it
                    it.ivTocSort.rotationX = if (tocReversed) 0f else 180f
                    it.ivTocSort.setOnClickListener { toggleTocOrder() }
                    upTocHeader()
                }
            }
        }
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) { upLoading(false, it) }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        editMenuItem = menu.findItem(R.id.menu_edit)
        menuCustomBtn = menu.findItem(R.id.menu_custom_btn).also {
            it.isVisible = viewModel.bookSource?.customButton == true
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt ?: false
        menu.findItem(R.id.menu_upload)?.isVisible =
            viewModel.bookData.value?.isLocal ?: false
        menu.findItem(R.id.menu_auto_task_book_update)?.isVisible =
            viewModel.inBookshelf
        menu.findItem(R.id.menu_delete_alert)?.isChecked =
            LocalConfig.bookInfoDeleteAlert
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    infoEditResult.launch {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_custom_btn -> {
                viewModel.getBook()?.let { book ->
                    SourceCallBack.callBackBtn(
                        this, SourceCallBack.CLICK_CUSTOM_BUTTON,
                        viewModel.bookSource, book, null
                    )
                }
            }

            R.id.menu_share_it -> {
                viewModel.getBook()?.let { book ->
                    val bookJson = GSON.toJson(book)
                    val shareStr = "${book.bookUrl}#$bookJson"
                    SourceCallBack.callBackBtn(
                        this, SourceCallBack.CLICK_SHARE_BOOK,
                        viewModel.bookSource, book, null, shareStr
                    ) {
                        shareWithQr(shareStr, book.name)
                    }
                }
            }

            R.id.menu_refresh -> {
                refreshBook()
            }

            R.id.menu_auto_task_book_update -> {
                showAutoTaskDialog()
            }

            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }

            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.let { book ->
                SourceCallBack.callBackBtn(
                    this, SourceCallBack.CLICK_COPY_BOOK_URL,
                    viewModel.bookSource, book, null, book.bookUrl
                ) {
                    sendToClip(book.bookUrl)
                }
            }

            R.id.menu_copy_toc_url -> viewModel.getBook()?.let { book ->
                val tocUrl = book.tocUrl
                SourceCallBack.callBackBtn(
                    this, SourceCallBack.CLICK_COPY_TOC_URL,
                    viewModel.bookSource, book, null, tocUrl
                ) {
                    sendToClip(tocUrl)
                }
            }

            R.id.menu_can_update -> {
                viewModel.getBook()?.let {
                    it.canUpdate = !it.canUpdate
                    if (viewModel.inBookshelf) {
                        if (!it.canUpdate) {
                            it.removeType(BookType.updateError)
                        }
                        viewModel.saveBook(it)
                    }
                }
            }

            R.id.menu_clear_cache -> {
                viewModel.getBook()?.let { book ->
                    SourceCallBack.callBackBtn(
                        this, SourceCallBack.CLICK_CLEAR_CACHE,
                        viewModel.bookSource, book, null
                    ) {
                        viewModel.clearCache()
                    }
                } ?: viewModel.clearCache()
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                viewModel.getBook()?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }

            R.id.menu_delete_alert -> LocalConfig.bookInfoDeleteAlert = !item.isChecked
            R.id.menu_upload -> {
                viewModel.getBook()?.let { book ->
                    book.getRemoteUrl()?.let {
                        alert(R.string.draw, R.string.sure_upload) {
                            okButton {
                                upLoadBook(book)
                            }
                            cancelButton()
                        }
                    } ?: upLoadBook(book)
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            // tv_intro 存在于且仅存在于其中一侧(portrait=headerBinding,land=binding),elvis 取现存的那个
            val tvIntro = headerBinding?.tvIntro ?: binding.tvIntro
            currentFocus?.let {
                if (it === tvIntro && tvIntro.hasSelection()) {
                    it.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshBook() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun upLoadBook(
        book: Book,
        bookWebDav: RemoteBookWebDav? = AppWebDav.defaultBookWebDav,
    ) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                bookWebDav
                    ?.upload(book)
                    ?: throw NoStackTraceException("未配置webDav")
                //更新书籍最后更新时间,使之比远程书籍的时间新
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun showBook(book: Book) = binding.run {
        showCover(book)
        tvName.text = book.name
        tvToolbarTitle.text = book.name
        tvAuthor.text = getString(R.string.author_show, book.getRealAuthor())
        // tvOrigin/tvLasted/tvIntro/llToc 存在于且仅存在于其中一侧:
        // portrait 已随 ll_info 迁入 header(RecyclerView addHeaderView 异步 inflate,可能晚于本次数据到达,
        // 故 headerBinding 用 null-safe 更新,header 自己 inflate 时也会在 bindInfoHeader 内主动拉一次);
        // land 未改动,字段仍在 binding 本身(nullable,因 portrait 侧同 id 已不存在于 activity 布局)。
        headerBinding?.let { h ->
            h.tvOrigin.text = getString(R.string.origin_show, book.originName)
            h.tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
            h.tvIntro.text = book.getDisplayIntro()
            h.llToc.visible(!book.isWebFile)
        }
        tvOrigin?.text = getString(R.string.origin_show, book.originName)
        tvLasted?.text = getString(R.string.lasted_show, book.latestChapterTitle)
        tvIntro?.text = book.getDisplayIntro()
        llToc?.visible(!book.isWebFile)
        menuCustomBtn?.isVisible = viewModel.bookSource?.customButton == true
        upTvBookshelf()
        upKinds(book)
        upGroup(book.group)
    }

    /**
     * header(信息卡+简介+目录区头)绑定:施色卡角(运行时 MaterialShapeDrawable,复刻原 ll_info 逻辑,
     * 仅 portrait 需要——appBar!=null 即 portrait,land 无 recyclerView/header 不会走到这里)+
     * 监听迁移(原 initViewEvent 里对这些 view 的 setOnClickListener/setOnLongClickListener 整体搬入,
     * SourceCallBack 钩子体一字不改)+数据回填(header 可能晚于 showBook 到达,主动拉一次当前数据)。
     */
    private fun bindInfoHeader(h: ItemBookInfoHeaderBinding) {
        if (binding.appBar != null) {
            h.root.background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setTopLeftCornerSize(resources.getDimension(R.dimen.radius_xl))
                    .setTopRightCornerSize(resources.getDimension(R.dimen.radius_xl))
                    .build()
            ).apply {
                fillColor = ColorStateList.valueOf(backgroundColor)
            }
        }
        h.tvIntro.revealOnFocusHint = false
        h.tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        h.tvChangeSource.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }
        h.tvTocView.setOnClickListener {
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) {
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        h.llToc.setOnClickListener { h.tvTocView.performClick() }
        h.tvChangeGroup.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            }
        }
        PressSpringEffect.attach(h.tvChangeSource)
        PressSpringEffect.attach(h.tvChangeGroup)
        PressSpringEffect.attach(h.tvTocView)
        // header 可能晚于 viewModel 数据就绪才 inflate,主动拉一次当前数据回填
        viewModel.bookData.value?.let { book ->
            h.tvOrigin.text = getString(R.string.origin_show, book.originName)
            h.tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
            h.tvIntro.text = book.getDisplayIntro()
            h.llToc.visible(!book.isWebFile)
        }
        // tv_toc 同理回填:章节列表可能已到达(或仍在 loading/失败态),按 upLoading 的三态口径补一次
        val chapterList = viewModel.chapterListData.value
        h.tvToc.text = when {
            chapterList == null -> getString(R.string.toc_s, getString(R.string.loading))
            chapterList.isEmpty() -> getString(R.string.toc_s, getString(R.string.error_load_toc))
            else -> getString(R.string.toc_s, book?.durChapterTitle ?: getString(R.string.loading))
        }
    }

    private fun upKinds(book: Book) = binding.run {
        lifecycleScope.launch {
            var kinds = book.getKindList()
            if (book.isLocal) {
                withContext(IO) {
                    val size = FileDoc.fromFile(book.bookUrl).size
                    if (size > 0) {
                        kinds = kinds.toMutableList()
                        kinds.add(ConvertUtils.formatFileSize(size))
                    }
                }
            }
            if (kinds.isEmpty()) {
                lbKind.gone()
            } else {
                lbKind.visible()
                val source = viewModel.bookSource
                lbKind.setLabels(
                    kinds,
                    { kind ->
                        source?.let {
                            SourceCallBack.callBackBtn(
                                this@BookInfoActivity,
                                SourceCallBack.CLICK_BOOK_LABEL,
                                source, book, null, result = kind
                            ) {
                                SearchActivity.start(this@BookInfoActivity, kind)
                            }
                        }
                    },
                    { kind ->
                        source?.let {
                            SourceCallBack.callBackBtn(
                                this@BookInfoActivity,
                                SourceCallBack.LONG_CLICK_BOOK_LABEL,
                                source, book, null, result = kind
                            )
                        }
                        true
                    }
                )
            }
        }
    }

    private fun showCover(book: Book) {
        val coverOrigin = book.getCoverSourceOrigin()
        binding.ivCover.load(book.getDisplayCover(), book.name, book.author, false, coverOrigin) {
            binding.ivCover.post { applyAmbientHeader() }
        }
    }

    private fun applyAmbientHeader() {
        // N3a 详情头图与 N3b 音频页共用氛围背景实现(utils/AmbientBackground.kt)
        (binding.appBar ?: binding.llHeaderPanel)
            ?.applyAmbientBackground(binding.ivCover.drawable, lifecycleScope) { isDestroyed }
    }

    /**
     * N3a toc-listify 分治:portrait 有 recyclerView(tv_toc/tv_lasted 已迁入 header),
     * land 无 recyclerView(tv_toc/tv_lasted 仍是 activity 自己的旧字段)——与 appBar 判别器同理。
     * tvToc/tvLasted 均按此discriminator 取目标 view,when 分支结构不变,仅目标 view 来源变化。
     */
    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        val isPortrait = binding.recyclerView != null
        val tvToc = if (isPortrait) headerBinding?.tvToc else binding.tvToc
        val tvLasted = if (isPortrait) headerBinding?.tvLasted else binding.tvLasted
        when {
            isLoading -> {
                tvToc?.text = getString(R.string.toc_s, getString(R.string.loading))
            }

            chapterList.isNullOrEmpty() -> {
                tvToc?.text = getString(
                    R.string.toc_s,
                    getString(R.string.error_load_toc)
                )
            }

            else -> {
                book?.let {
                    tvToc?.text = getString(R.string.toc_s, it.durChapterTitle)
                    tvLasted?.text = getString(R.string.lasted_show, it.latestChapterTitle)
                }
            }
        }
        if (isPortrait) {
            chapterList?.let { upChapterList(it) }
        } else {
            upTocPreview(chapterList)
        }
    }

    /**
     * portrait 专属:详情页内嵌完整目录(RecyclerView + ChapterListAdapter),FLAT 喂入
     * (无 TocListState 分卷分组,保持"倒序即最新在前"的干净反转——N3a 计划最大偏差点)。
     */
    private fun upChapterList(chapters: List<BookChapter>) {
        fullChapters = chapters
        submitTocItems()
    }

    private fun submitTocItems() {
        val ordered = if (tocReversed) fullChapters.asReversed() else fullChapters
        chapterAdapter.setItems(ordered.map { TocListItem.Chapter(chapter = it, depth = 0) })
        upTocHeader()
    }

    private fun toggleTocOrder() {
        tocReversed = !tocReversed
        tocHeaderBinding?.ivTocSort?.rotationX = if (tocReversed) 0f else 180f
        submitTocItems()
    }

    private fun upTocHeader() {
        tocHeaderBinding?.tvTocCount?.text = getString(R.string.toc_s, fullChapters.size.toString())
    }

    /**
     * 详情页内嵌目录预览(land 专属)：取最新 5 章倒序（列表尾=最新）填充可点行，
     * 点击直接定位到该章开始阅读，无需先进入目录页。
     */
    private fun upTocPreview(chapterList: List<BookChapter>?) {
        if (chapterList.isNullOrEmpty()) {
            binding.llTocPreview?.gone()
            return
        }
        binding.llTocPreview?.removeAllViews()
        val total = chapterList.size
        chapterList.takeLast(5).reversed().forEachIndexed { i, chapter ->
            binding.llTocPreview?.addView(buildTocPreviewRow(chapter, total - 1 - i))
        }
        binding.llTocPreview?.visible()
    }

    private fun buildTocPreviewRow(chapter: BookChapter, index: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        // M3 单行列表标准 56dp(同全 app 偏好/管理行),44dp 曾致行密+误触(真机验收实锤)
        minHeight = 56.dpToPx()
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(AppColorScheme.current.onSurfaceVariant)
        val hPad = resources.getDimensionPixelSize(R.dimen.space_l)
        setPadding(hPad, 0, hPad, 0)
        background = ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)
        isClickable = true
        isFocusable = true
        text = chapter.title
        setOnClickListener { readFromChapter(index) }
    }

    private fun upTvBookshelf() {
        if (viewModel.inBookshelf) {
            binding.tvShelf.text = getString(R.string.remove_from_bookshelf)
        } else {
            binding.tvShelf.text = getString(R.string.add_to_bookshelf)
        }
        editMenuItem?.isVisible = viewModel.inBookshelf
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            val text = if (it.isNullOrEmpty()) {
                if (book?.isLocal == true) {
                    getString(R.string.group_s, getString(R.string.local_no_group))
                } else {
                    getString(R.string.group_s, getString(R.string.no_group))
                }
            } else {
                getString(R.string.group_s, it)
            }
            // tv_group 存在于且仅存在于其中一侧(portrait=headerBinding,land=binding)
            headerBinding?.tvGroup?.text = text
            binding.tvGroup?.text = text
        }
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        ivCover.setOnLongClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path))
            }
            true
        }
        tvRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isWebFile) {
                    showWebFileDownloadAlert {
                        readBook(it)
                    }
                } else {
                    readBook(book)
                }
            }
        }
        tvShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf {
                            upTvBookshelf()
                        }
                    }
                }
            }
        }
        tvOrigin?.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        tvChangeSource?.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }
        tvTocView?.setOnClickListener {
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) {
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        llToc?.setOnClickListener { tvTocView?.performClick() }
        tvChangeGroup?.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            }
        }
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity, SourceCallBack.CLICK_AUTHOR,
                    viewModel.bookSource, book, null, book.getRealAuthor()
                ) {
                    startActivity<SearchActivity> {
                        putExtra("key", book.author)
                    }
                }
            }
        }
        tvAuthor.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_AUTHOR,
                    viewModel.bookSource, book, null, result = book.author
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
            true
        }
        tvName.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity, SourceCallBack.CLICK_BOOK_NAME,
                    viewModel.bookSource, book, null, book.name
                ) {
                    startActivity<SearchActivity> {
                        putExtra("key", book.name)
                    }
                }
            }
        }
        tvName.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_BOOK_NAME,
                    viewModel.bookSource, book, null, result = book.name
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
            true
        }
        refreshLayout.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            refreshBook()
        }
        PressSpringEffect.attach(tvShelf)
        PressSpringEffect.attach(tvRead)
        tvChangeSource?.let { PressSpringEffect.attach(it) }
        tvChangeGroup?.let { PressSpringEffect.attach(it) }
        tvTocView?.let { PressSpringEffect.attach(it) }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let {
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del
                ) {
                    var checkBox: CheckBox? = null
                    if (it.isLocal) {
                        checkBox = CheckBox(this@BookInfoActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }
                    yesButton {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    noButton()
                }
            } else {
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    /**
     * 定位到指定章节并进入阅读界面。原为 tocActivityResult 回调内联逻辑，
     * 现同时供目录页返回与详情页目录预览行点击复用。
     */
    private fun readFromChapter(index: Int, pos: Int = 0, changed: Boolean = false) {
        viewModel.getBook(false)?.let { book ->
            lifecycleScope.launch {
                withContext(IO) {
                    book.durChapterIndex = index
                    book.durChapterPos = pos
                    chapterChanged = changed
                    appDb.bookDao.update(book)
                }
                startReadActivity(book)
            }
        }
    }

    private data class BookAutoTaskConfig(
        val enabled: Boolean,
        val notifyEnabled: Boolean,
        val cacheEnabled: Boolean,
        val intervalHours: Int
    )

    private fun showAutoTaskDialog() {
        val book = viewModel.getBook() ?: return
        val taskId = AutoTask.bookTaskId(book.bookUrl)
        val existing = AutoTask.getRules().firstOrNull { it.id == taskId }
        val config = parseBookAutoTaskConfig(existing, book.bookUrl)
        val dialogBinding = DialogBookAutoTaskBinding.inflate(layoutInflater)
        dialogBinding.switchEnable.isChecked = config.enabled
        dialogBinding.switchNotify.isChecked = config.notifyEnabled
        dialogBinding.switchCache.isChecked = config.cacheEnabled
        dialogBinding.editInterval.setText(config.intervalHours.toString())
        alert(R.string.auto_task_book_update) {
            customView { dialogBinding.root }
            okButton {
                val enabled = dialogBinding.switchEnable.isChecked
                val hours = dialogBinding.editInterval.text?.toString()?.trim()?.toIntOrNull()
                    ?.coerceAtLeast(1)
                    ?: cachedOrDefaultIntervalHours
                val notifyEnabled = dialogBinding.switchNotify.isChecked
                val cacheEnabled = dialogBinding.switchCache.isChecked
                val cron = "0 */$hours * * *"
                val script = AutoTask.buildBookUpdateScript(
                    bookUrl = book.bookUrl,
                    notifyEnabled = notifyEnabled,
                    cacheEnabled = cacheEnabled
                )
                val displayName = book.name.ifBlank { book.bookUrl }
                val updated = (existing ?: AutoTaskRule(id = taskId)).copy(
                    id = taskId,
                    name = getString(R.string.auto_task_book_update_name, displayName),
                    enable = enabled,
                    cron = cron,
                    script = script
                )
                AutoTask.upsert(updated)
                LocalConfig.bookAutoTaskIntervalHours = hours
                if (enabled) {
                    toastOnUi(R.string.auto_task_book_update_saved)
                } else {
                    toastOnUi(R.string.auto_task_book_update_deleted)
                }
            }
            cancelButton()
        }
    }

    private fun parseBookAutoTaskConfig(
        task: AutoTaskRule?,
        bookUrl: String
    ): BookAutoTaskConfig {
        if (task == null) {
            return BookAutoTaskConfig(
                enabled = true,
                notifyEnabled = true,
                cacheEnabled = false,
                intervalHours = cachedOrDefaultIntervalHours
            )
        }
        val interval = parseCronHours(task.cron)
            ?: cachedOrDefaultIntervalHours
        var notifyEnabled = true
        var cacheEnabled = false
        val json = extractTaskJson(task.script)
        if (!json.isNullOrBlank()) {
            val root = GSON.fromJsonObject<Map<String, Any?>>(json).getOrNull()
            val action = findRefreshAction(root, bookUrl)
            val notify = toStringKeyMap(action?.get("notify"))
            val cache = toStringKeyMap(action?.get("cache"))
            notifyEnabled = readBoolean(notify, "enable", true)
            cacheEnabled = readBoolean(cache, "enable", false)
        }
        return BookAutoTaskConfig(
            enabled = task.enable,
            notifyEnabled = notifyEnabled,
            cacheEnabled = cacheEnabled,
            intervalHours = interval
        )
    }

    private fun extractTaskJson(script: String?): String? {
        if (script.isNullOrBlank()) return null
        val normalized = AutoTask.normalizeScript(script)
        val trimmed = normalized.trim()
        val assignMatch = Regex("^(?:var|let|const)\\s+[\\w$]+\\s*=\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(trimmed)
        if (assignMatch != null) {
            val assigned = assignMatch.groupValues[1].trim()
            val first = assigned.substringBefore(";").trim()
            val inner = unwrapJsonExpression(first) ?: first
            if (inner.startsWith("{") || inner.startsWith("[")) {
                return inner
            }
        }
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.startsWith("{") || inner.startsWith("[")) {
                return inner
            }
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val index = trimmed.indexOf("return")
        if (index < 0) return null
        val after = trimmed.substring(index + 6).trim()
        if (after.isBlank()) return null
        return after.substringBefore(";").trim()
    }

    private fun unwrapJsonExpression(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        return null
    }

    // 解析 cron 获取间隔小时数，兼容旧版分钟格式 `*/N * * * *` 和新版 `0 */N * * *`
    private fun parseCronHours(cron: String?): Int? {
        if (cron.isNullOrBlank()) return null
        val trimmed = cron.trim()
        // 新版：每小时执行 `0 */N * * *`
        Regex("^\\s*0\\s+\\*/(\\d+)\\s+\\*\\s+\\*\\s+\\*\\s*$")
            .find(trimmed)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        // 旧版：每N分钟 `*/N * * * *`，转换为小时（向上取整，最小1小时）
        Regex("^\\s*\\*/(\\d+)\\s+\\*\\s+\\*\\s+\\*\\s+\\*\\s*$")
            .find(trimmed)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { minutes ->
                return ceil(minutes / 60.0).toInt().coerceAtLeast(1)
            }
        return null
    }

    private val cachedOrDefaultIntervalHours: Int
        get() {
            val cached = LocalConfig.bookAutoTaskIntervalHours
            return if (cached > 0) cached else 1
        }

    private fun findRefreshAction(
        root: Map<String, Any?>?,
        bookUrl: String
    ): Map<String, Any?>? {
        if (root == null) return null
        val actions = when (val actionsValue = root["actions"]) {
            is List<*> -> actionsValue
            else -> if (root.containsKey("type")) listOf(root) else emptyList()
        }
        return actions.mapNotNull { toStringKeyMap(it) }
            .firstOrNull {
                val type = it["type"]?.toString()
                val url = it["bookUrl"]?.toString()
                type.equals("refreshToc", true) && url == bookUrl
            }
    }

    private fun toStringKeyMap(value: Any?): Map<String, Any?>? {
        return when (value) {
            is Map<*, *> -> {
                val out = LinkedHashMap<String, Any?>()
                value.forEach { (k, v) ->
                    if (k != null) out[k.toString()] = v
                }
                out
            }
            else -> null
        }
    }

    private fun readBoolean(
        map: Map<String, Any?>?,
        key: String,
        defaultValue: Boolean
    ): Boolean {
        if (map == null) return defaultValue
        return when (val value = getValueIgnoreCase(map, key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", true) || value == "1"
            else -> defaultValue
        }
    }

    private fun getValueIgnoreCase(map: Map<String, Any?>, key: String): Any? {
        map[key]?.let { return it }
        return map.entries.firstOrNull { it.key.equals(key, true) }?.value
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(
            R.string.download_and_import_file,
            webFiles
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun startReadActivity(book: Book) {
        val options = if (MotionTokens.enabled) {
            ActivityOptionsCompat.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
        } else null
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf),
                options,
            )

            else -> readBookResult.launch(
                Intent(
                    this,
                    if (!book.isLocal && book.isImage && AppConfig.showMangaUi) ReadMangaActivity::class.java
                    else ReadBookActivity::class.java
                )
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("chapterChanged", chapterChanged),
                options,
            )
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            showCover(book)
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {
                    upTvBookshelf()
                }
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        val showText = "Loading....."
        if (isShow) {
            waitDialog.run {
                setText(showText)
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

}
