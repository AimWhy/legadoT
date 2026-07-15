package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.databinding.ItemExploreGroupChipBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.explore.manage.ExploreContainerEditDialog
import io.legado.app.ui.main.explore.manage.ExploreManageActivity
import io.legado.app.ui.widget.popupActionMenu
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发现界面(容器卡片流)
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreContainerAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreContainerAdapter(requireContext(), this) }
    private var openingExplore = false
    private var allStates: List<ExploreContainerState> = emptyList()
    private var currentGroups: List<String> = emptyList()
    private var rebuildingChips = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initRecyclerView()
        observeData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_manage -> startActivity<ExploreManageActivity>()
        }
    }

    private fun initRecyclerView() {
        binding.rvContainers.setEdgeEffectColor(primaryColor)
        binding.rvContainers.layoutManager = LinearLayoutManager(context)
        binding.rvContainers.adapter = adapter
        binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            binding.refreshLayout.isRefreshing = false
            viewModel.refreshAll(effectiveGroup())
        }
        binding.cgGroups.setOnCheckedStateChangeListener { group, checkedIds ->
            if (rebuildingChips) return@setOnCheckedStateChangeListener
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val g = group.findViewById<Chip>(id)?.tag as? String
                ?: return@setOnCheckedStateChangeListener
            requireContext().putPrefString(PreferKey.exploreGroup, g)
            upDisplayStates()
        }
        binding.btnAddContainer.setOnClickListener {
            startActivity<ExploreManageActivity>()
        }
    }

    private fun observeData() {
        viewModel.statesData.observe(viewLifecycleOwner) { states ->
            allStates = states
            binding.llEmpty.isGone = states.isNotEmpty()
            upGroupChips()
            upDisplayStates()
        }
        viewModel.upBookshelfLiveData.observe(viewLifecycleOwner) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, "isInBookshelf")
        }
    }

    private fun selectedGroupPref(): String =
        requireContext().getPrefString(PreferKey.exploreGroup) ?: ""

    /** 已存分组不在当前分组集时显示回退"全部";pref 保留,重建同名分组自动恢复选中 */
    private fun effectiveGroup(): String {
        val saved = selectedGroupPref()
        return if (saved.isNotEmpty() && saved in currentGroups) saved else ""
    }

    private fun upDisplayStates() {
        val group = effectiveGroup()
        val display = if (group.isEmpty()) allStates
        else allStates.filter { it.container.groupName == group }
        adapter.setItems(display, adapter.diffItemCallBack)
    }

    private fun upGroupChips() {
        val groups = allStates.map { it.container.groupName }
            .filter { it.isNotEmpty() }.distinct()
        if (groups == currentGroups && binding.cgGroups.childCount > 0) {
            return // 分组集未变不重建,保住横向滚动位置
        }
        currentGroups = groups
        binding.hostGroups.isGone = groups.isEmpty()
        rebuildingChips = true
        binding.cgGroups.removeAllViews()
        val effective = effectiveGroup()
        (listOf("") + groups).forEach { g ->
            val chip = ItemExploreGroupChipBinding
                .inflate(layoutInflater, binding.cgGroups, false).root
            chip.id = View.generateViewId()
            chip.tag = g
            chip.text = g.ifEmpty { getString(R.string.all) }
            chip.isChecked = g == effective
            binding.cgGroups.addView(chip)
        }
        rebuildingChips = false
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvContainers.scrollToPosition(0)
        } else {
            binding.rvContainers.smoothScrollToPosition(0)
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    override fun showBookInfo(book: SearchBook) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(IO) {
                runCatching { appDb.searchBookDao.insert(book) }
            }
            startActivity<BookInfoActivity> {
                putExtra("name", book.name)
                putExtra("author", book.author)
                putExtra("bookUrl", book.bookUrl)
            }
        }
    }

    override fun openExplore(state: ExploreContainerState) {
        if (openingExplore) return
        openingExplore = true
        val container = state.container
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val source = withContext(IO) {
                    appDb.bookSourceDao.getBookSource(container.sourceUrl)
                }
                if (source == null) {
                    toastOnUi(R.string.explore_source_not_found)
                    return@launch
                }
                val url = ExploreContainerHelp.resolveKindUrl(
                    source.exploreKinds(), container.kindTitle, container.kindUrl
                )
                startActivity<ExploreShowActivity> {
                    putExtra("exploreName", container.getDisplayTitle())
                    putExtra("sourceUrl", container.sourceUrl)
                    putExtra("exploreUrl", url)
                }
            } finally {
                openingExplore = false
            }
        }
    }

    override fun refreshContainer(state: ExploreContainerState) {
        viewModel.refreshContainer(state.container.id)
    }

    override fun nextBatch(state: ExploreContainerState) {
        viewModel.nextBatch(state.container.id)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStale()
        // 相对时间标签防陈旧:回前台整列 payload 刷新
        adapter.notifyItemRangeChanged(0, adapter.itemCount, "time")
    }

    /** 长按菜单:登录仅在书源配置了登录地址时显示,搜索/登录直接作用于容器所属书源 */
    override fun showContainerMenu(anchor: View, state: ExploreContainerState) {
        val container = state.container
        viewLifecycleOwner.lifecycleScope.launch {
            val source = withContext(IO) {
                appDb.bookSourceDao.getBookSource(container.sourceUrl)
            }
            popupActionMenu(requireContext()) {
                item(getString(R.string.refresh), "refresh")
                item(getString(R.string.search), "search", source != null)
                item(getString(R.string.login), "login", !source?.loginUrl.isNullOrBlank())
                item(getString(R.string.edit), "edit")
                item(getString(R.string.delete), "delete")
                danger("delete")
            }.show(anchor) { action ->
                when (action) {
                    "refresh" -> refreshContainer(state)
                    "search" -> source?.let {
                        startActivity<SearchActivity> {
                            putExtra("searchScope", SearchScope(it).toString())
                        }
                    }

                    "login" -> source?.let {
                        startActivity<SourceLoginActivity> {
                            putExtra("type", "bookSource")
                            putExtra("key", it.bookSourceUrl)
                        }
                    }

                    "edit" -> editContainer(state)
                    "delete" -> deleteContainer(state)
                }
            }
        }
    }

    private fun editContainer(state: ExploreContainerState) {
        showDialogFragment(ExploreContainerEditDialog.edit(state.container.id))
    }

    private fun deleteContainer(state: ExploreContainerState) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + state.container.getDisplayTitle())
            noButton()
            yesButton { viewModel.deleteContainer(state.container) }
        }
    }
}
