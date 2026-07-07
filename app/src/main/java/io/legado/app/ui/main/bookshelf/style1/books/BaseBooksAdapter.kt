package io.legado.app.ui.main.bookshelf.style1.books

import android.content.Context
import android.view.View
import androidx.core.os.bundleOf
import androidx.viewbinding.ViewBinding
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.ui.main.bookshelf.BookDiffItemCallback

abstract class BaseBooksAdapter<VB : ViewBinding>(context: Context) :
    DiffRecyclerAdapter<Book, VB>(context) {

    override val keepScrollPosition = true

    override val diffItemCallback = BookDiffItemCallback

    override fun onViewRecycled(holder: ItemViewHolder) {
        super.onViewRecycled(holder)
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
    }

    fun notification(bookUrl: String) {
        getItems().forEachIndexed { i, it ->
            if (it.bookUrl == bookUrl) {
                notifyItemChanged(i, bundleOf(Pair("refresh", null), Pair("lastUpdateTime", null)))
                return
            }
        }
    }

    fun upLastUpdateTime() {
        notifyItemRangeChanged(0, itemCount, bundleOf(Pair("lastUpdateTime", null)))
    }

    interface CallBack {
        fun open(book: Book)
        fun openBookInfo(book: Book, cover: View? = null)
        fun isUpdate(bookUrl: String): Boolean
    }
}
