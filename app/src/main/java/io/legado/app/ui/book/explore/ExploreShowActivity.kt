package io.legado.app.ui.book.explore

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ActivityExploreShowBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ViewLoadMoreBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.widget.recycler.LoadMoreView
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 发现列表
 */
class ExploreShowActivity : VMBaseActivity<ActivityExploreShowBinding, ExploreShowViewModel>(),
    ExploreShowAdapter.CallBack {
    override val binding by viewBinding(ActivityExploreShowBinding::inflate)
    override val viewModel by viewModels<ExploreShowViewModel>()

    private val adapter by lazy { ExploreShowAdapter(this, this) }
    private val loadMoreView by lazy { LoadMoreView(this) }
    private var jumpMenuItem: MenuItem? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initRecyclerView()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.clearBooksLiveData.observe(this) {
            adapter.setItems(emptyList())
            loadMoreView.startLoad()
        }
        viewModel.currentPageLiveData.observe(this) {
            updateJumpMenuTitle(it)
        }
        viewModel.initData(intent)
        viewModel.errorLiveData.observe(this) {
            loadMoreView.error(it)
        }
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explore_show, menu)
        jumpMenuItem = menu.findItem(R.id.menu_jump)
        updateJumpMenuTitle(viewModel.currentPageLiveData.value ?: 1)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_jump -> alertJumpPage()
            R.id.menu_add_all_to_bookshelf -> alertAddLoadedBooksToShelf()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun updateJumpMenuTitle(page: Int) {
        jumpMenuItem?.title = getString(R.string.page_number_format, page)
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
        adapter.addFooterView {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        loadMoreView.startLoad()
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading) {
                scrollToBottom(true)
            }
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    scrollToBottom()
                }
            }
        })
    }

    private fun alertJumpPage() {
        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.input_page_number)
            editView.inputType = InputType.TYPE_CLASS_NUMBER
        }
        alert(R.string.jump) {
            customView { alertBinding.root }
            yesButton {
                val page = alertBinding.editView.text?.toString()?.trim()?.toIntOrNull()
                if (page == null || page < 1) {
                    toastOnUi(R.string.invalid_page_number)
                    return@yesButton
                }
                viewModel.jumpToPage(page)
            }
            noButton()
        }
    }

    private fun alertAddLoadedBooksToShelf() {
        val books = viewModel.getLoadedBooks()
        if (books.isEmpty()) {
            toastOnUi(R.string.no_loaded_books_to_add)
            return
        }
        val skipped = books.count { viewModel.isInBookShelf(it) }
        val addCount = books.size - skipped
        if (addCount <= 0) {
            toastOnUi(R.string.loaded_books_all_in_bookshelf)
            return
        }
        alert(R.string.add_all_to_bookshelf) {
            setMessage(getString(R.string.add_loaded_books_to_shelf_message, books.size, addCount, skipped))
            yesButton {
                viewModel.addLoadedBooksToShelf { result ->
                    toastOnUi(
                        getString(
                            R.string.add_loaded_books_to_shelf_result,
                            result.added,
                            result.skipped
                        )
                    )
                }
            }
            noButton()
        }
    }

    private fun scrollToBottom(forceLoad: Boolean = false) {
        if ((loadMoreView.hasMore && !loadMoreView.isLoading) || forceLoad) {
            loadMoreView.hasMore()
            viewModel.explore()
        }
    }

    private fun upData(books: List<SearchBook>) {
        loadMoreView.stopLoad()
        if (books.isEmpty() && adapter.isEmpty()) {
            loadMoreView.noMore(getString(R.string.empty))
        } else if (adapter.getActualItemCount() == books.size) {
            loadMoreView.noMore()
        } else {
            adapter.setItems(books)
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    override fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }
}
