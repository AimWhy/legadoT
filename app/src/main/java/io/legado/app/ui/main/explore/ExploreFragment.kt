package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.explore.manage.ExploreContainerEditDialog
import io.legado.app.ui.main.explore.manage.ExploreManageActivity
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
            viewModel.refreshAll()
        }
        binding.btnAddContainer.setOnClickListener {
            startActivity<ExploreManageActivity>()
        }
    }

    private fun observeData() {
        viewModel.statesData.observe(viewLifecycleOwner) { states ->
            binding.llEmpty.isGone = states.isNotEmpty()
            adapter.setItems(states, adapter.diffItemCallBack)
        }
        viewModel.upBookshelfLiveData.observe(viewLifecycleOwner) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, "isInBookshelf")
        }
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

    override fun editContainer(state: ExploreContainerState) {
        showDialogFragment(ExploreContainerEditDialog.edit(state.container.id))
    }

    override fun deleteContainer(state: ExploreContainerState) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + state.container.getDisplayTitle())
            noButton()
            yesButton { viewModel.deleteContainer(state.container) }
        }
    }
}
