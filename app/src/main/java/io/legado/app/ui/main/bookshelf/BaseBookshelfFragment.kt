package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.indices
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ViewBookshelfHeaderBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.readProgress
import io.legado.app.help.config.AppConfig
import io.legado.app.help.motion.PressSpringEffect
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.getCheckedIndex
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseBookshelfFragment(layoutId: Int) : VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    private val importBookshelf = registerForActivityResult(HandleFileContract()) { result ->
        kotlin.runCatching {
            result.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure { error ->
            toastOnUi(error.localizedMessage ?: "ERROR")
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }
    abstract val groupId: Long
    abstract val books: List<Book>
    private var groupsLiveData: LiveData<List<BookGroup>>? = null
    private val waitDialog by lazy {
        WaitDialog(requireContext()).apply {
            setOnCancelListener {
                viewModel.addBookJob?.cancel()
            }
        }
    }

    private var shelfHeaderBinding: ViewBookshelfHeaderBinding? = null
    private var heroBook: Book? = null
    private var shelfHeaderFlowJob: Job? = null

    private var shelfRefreshLayout: SwipeRefreshLayout? = null

    /**
     * shelfHeaderBinding/shelfRefreshLayout 等是 Fragment 级字段持有的 view 引用,
     * view 销毁后若不置空会悬空持有(BooksFragment.onDestroyView 同款既有约定)。
     * shelfHeaderFlowJob 挂在 viewLifecycleOwner.lifecycleScope,view 销毁时本已自动取消,
     * 此处显式 cancel+置空是防御性收尾,避免持有已失效 Job 引用。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        shelfHeaderFlowJob?.cancel()
        shelfHeaderFlowJob = null
        shelfHeaderBinding = null
        shelfRefreshLayout = null
    }

    abstract fun gotoTop()

    /**
     * 下拉刷新是否允许——数据侧闸门(分组开关/空书架等),子类按需覆写。
     * 经 [syncRefreshEnable] 收敛为单一写入点,避免多处直写 `refreshLayout.isEnabled`
     * 互相覆盖("三写互吞"缺陷)。
     */
    protected open fun refreshEnabledByData(): Boolean = true

    /**
     * 唯一允许写 `shelfRefreshLayout.isEnabled` 的地方。数据侧闸门变化后都应调用此函数
     * 重新计算,而不是直接赋值。
     */
    protected fun syncRefreshEnable() {
        shelfRefreshLayout?.isEnabled = refreshEnabledByData()
    }

    /**
     * 标题栏下方固定内容行挂接(style1/style2 共用,一处实现):统计行+续读行。
     * 标题职责已回归各布局的 TitleBar,此处只管数据行。
     * ① 续读行点击=继续阅读(startActivityForBook 既有链路)、长按=进详情。
     * ② 续读行按压弹性反馈。
     * ③ 订阅书籍表失效 Flow,统计/续读实时刷新。
     */
    fun bindShelfHeader(
        header: ViewBookshelfHeaderBinding,
        refreshLayout: SwipeRefreshLayout? = null
    ) {
        shelfHeaderBinding = header
        shelfRefreshLayout = refreshLayout
        header.continueReading.setOnClickListener {
            heroBook?.let { book -> startActivityForBook(book) }
        }
        header.continueReading.setOnLongClickListener {
            heroBook?.let { book -> openHeroBookInfo(book) }
            true
        }
        PressSpringEffect.attach(header.continueReading)
        subscribeShelfHeaderRefresh()
    }

    /**
     * hero 卡/统计随书架数据实时刷新:添加/导入书籍走的是 DialogFragment(非 Activity),
     * fragment 全程停留 RESUMED、onResume 不会重入,仅靠 onResume 单点调用会漏刷。
     * 订阅 BOOK_TABLE_NAME 的 InvalidationTracker,复刻 BooksFragment.upRecyclerData
     * 的 Flow 构造(同款 flowWithLifecycleAndDatabaseChangeFirst+catch+conflate+
     * flowOn(Dispatchers.Default)),RESUMED 门控下任何书籍表变更都会触发。
     * collect 落在 launch 默认的 Main dispatcher,直调 refreshShelfHeader()——
     * 该函数内部已自带 IO→Main 跳转,此处不重复 dispatch,避免竞态。
     * bindShelfHeader 只在 onFragmentCreated 调一次,故此订阅是一次性的。
     */
    private fun subscribeShelfHeaderRefresh() {
        shelfHeaderFlowJob?.cancel()
        shelfHeaderFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowAll().flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架大标题刷新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect {
                refreshShelfHeader()
            }
        }
    }

    /**
     * 刷新 hero 卡与统计副行:IO 读 lastReadBook+两计数 → 主线程回填。
     * 供 subscribeShelfHeaderRefresh 的书架数据变更订阅调用(bindShelfHeader 内已接线,
     * 覆盖初次绑定与后续任意书籍表变更,无需再靠 onResume 补刷)。
     */
    fun refreshShelfHeader() {
        val header = shelfHeaderBinding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val (book, bookCount, readingCount) = withContext(Dispatchers.IO) {
                Triple(
                    appDb.bookDao.lastReadBookOnShelf,
                    appDb.bookDao.allBookCount,
                    appDb.bookDao.readingCount
                )
            }
            heroBook = book
            header.tvShelfStats.text =
                getString(R.string.bookshelf_stats, bookCount, readingCount)
            if (book == null) {
                header.continueReading.gone()
                return@launch
            }
            header.continueReading.visible()
            header.tvContinueName.text = book.name
            header.tvContinueChapter.text = book.durChapterTitle.takeIf { it?.isNotBlank() == true } ?: getString(R.string.read_not_started)
            val progress = book.readProgress() ?: 0f
            header.tvContinuePercent.text = "${(progress * 100).toInt()}%"
        }
    }

    /** 续读入口长按进书籍详情(一行入口无封面视图,不做共享元素转场)。 */
    private fun openHeroBookInfo(book: Book) {
        val intent = Intent(requireContext(), BookInfoActivity::class.java)
            .putExtra("name", book.name)
            .putExtra("author", book.author)
        startActivity(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> showAddBookByUrlAlert()
            R.id.menu_bookshelf_manage -> startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) { file ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData =
                        HandleFileContract.FileData("bookshelf.json", file, "application/json")
                }
            }

            R.id.menu_import_bookshelf -> importBookshelfAlert(groupId)
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
    }

    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                waitDialog.dismiss()
            } else {
                waitDialog.setText("添加中... ($count)")
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showAddBookByUrlAlert() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    waitDialog.setText("添加中...")
                    waitDialog.show()
                    viewModel.addBookByUrl(it, groupId)
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            var bookshelfLayout = AppConfig.bookshelfLayout
            var bookshelfSort = AppConfig.bookshelfSort
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        spGroupStyle.setFilterValues(*resources.getStringArray(R.array.group_style))
                        if (AppConfig.bookGroupStyle !in 0..<spGroupStyle.itemCount) {
                            AppConfig.bookGroupStyle = 0
                        }
                        if (bookshelfLayout !in rgLayout.indices) {
                            bookshelfLayout = 0
                            AppConfig.bookshelfLayout = 0
                        }
                        if (bookshelfSort !in rgSort.indices) {
                            bookshelfSort = 0
                            AppConfig.bookshelfSort = 0
                        }
                        spGroupStyle.setSelectionByIndex(AppConfig.bookGroupStyle)
                        swShowUnread.isChecked = AppConfig.showUnread
                        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
                        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
                        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
                        rgLayout.checkByIndex(bookshelfLayout)
                        rgSort.checkByIndex(bookshelfSort)
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    var notifyMain = false
                    var recreate = false
                    if (AppConfig.bookGroupStyle != spGroupStyle.selectedItemPosition) {
                        AppConfig.bookGroupStyle = spGroupStyle.selectedItemPosition
                        notifyMain = true
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showLastUpdateTime != swShowLastUpdateTime.isChecked) {
                        AppConfig.showLastUpdateTime = swShowLastUpdateTime.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (AppConfig.showWaitUpCount != swShowWaitUpBooks.isChecked) {
                        AppConfig.showWaitUpCount = swShowWaitUpBooks.isChecked
                        activityViewModel.postUpBooksLiveData(true)
                    }
                    if (AppConfig.showBookshelfFastScroller != swShowBookshelfFastScroller.isChecked) {
                        AppConfig.showBookshelfFastScroller = swShowBookshelfFastScroller.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    if (bookshelfSort != rgSort.getCheckedIndex()) {
                        AppConfig.bookshelfSort = rgSort.getCheckedIndex()
                        upSort()
                    }
                    if (bookshelfLayout != rgLayout.getCheckedIndex()) {
                        AppConfig.bookshelfLayout = rgLayout.getCheckedIndex()
                        if (AppConfig.bookshelfLayout == 0) {
                            activityViewModel.booksGridRecycledViewPool.clear()
                        } else {
                            activityViewModel.booksListRecycledViewPool.clear()
                        }
                        recreate = true
                    }
                    if (recreate) {
                        postEvent(EventBus.RECREATE, "")
                    } else if (notifyMain) {
                        postEvent(EventBus.NOTIFY_MAIN, false)
                    }
                }
            }
            cancelButton()
        }
    }


    private fun importBookshelfAlert(groupId: Long) {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, groupId)
                }
            }
            cancelButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }

}
