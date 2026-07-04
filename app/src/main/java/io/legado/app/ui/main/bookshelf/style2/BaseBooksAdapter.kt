package io.legado.app.ui.main.bookshelf.style2

import android.content.Context
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.main.bookshelf.BookDiffItemCallback

abstract class BaseBooksAdapter<VH : RecyclerView.ViewHolder>(
    val context: Context,
    val callBack: CallBack
) : RecyclerView.Adapter<VH>() {

    protected val inflater: LayoutInflater = LayoutInflater.from(context)

    /** Book 分支委托共享 [BookDiffItemCallback]（style2 绑定路径无 lastUpdateTime case,收到忽略）;BookGroup 分支为混排特有 */
    private val diffItemCallback = object : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book ->
                    BookDiffItemCallback.areItemsTheSame(oldItem, newItem)

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupId == newItem.groupId
                }

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Book && newItem is Book ->
                    BookDiffItemCallback.areContentsTheSame(oldItem, newItem)

                oldItem is BookGroup && newItem is BookGroup -> {
                    oldItem.groupName == newItem.groupName &&
                            oldItem.cover == newItem.cover
                }

                else -> false
            }
        }

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
            when {
                oldItem is Book && newItem is Book ->
                    return BookDiffItemCallback.getChangePayload(oldItem, newItem)

                oldItem is BookGroup && newItem is BookGroup -> {
                    val bundle = bundleOf()
                    if (oldItem.groupName != newItem.groupName) {
                        bundle.putString("groupName", newItem.groupName)
                    }
                    if (oldItem.cover != newItem.cover) {
                        bundle.putString("cover", newItem.cover)
                    }
                    if (bundle.isEmpty) return null
                    return bundle
                }
            }
            return null
        }
    }

    private val asyncListDiffer by lazy {
        AsyncListDiffer(this, diffItemCallback)
    }

    fun updateItems() {
        asyncListDiffer.submitList(callBack.getItems())
    }

    fun notification(bookUrl: String) {
        for (i in 0 until itemCount) {
            getItem(i).let {
                if (it is Book && it.bookUrl == bookUrl) {
                    notifyItemChanged(i, bundleOf(Pair("refresh", null)))
                    return
                }
            }
        }
    }

    fun getItems() = asyncListDiffer.currentList

    fun getItem(position: Int) = getItems().getOrNull(position)

    override fun getItemCount(): Int {
        return getItems().size
    }

    override fun getItemViewType(position: Int): Int {
        if (getItem(position) is BookGroup) {
            return 1
        }
        return 0
    }

    final override fun onBindViewHolder(holder: VH, position: Int) {}


    interface CallBack {
        fun onItemClick(item: Any)
        fun onItemLongClick(item: Any)
        fun isUpdate(bookUrl: String): Boolean
        fun getItems(): List<Any>
    }
}