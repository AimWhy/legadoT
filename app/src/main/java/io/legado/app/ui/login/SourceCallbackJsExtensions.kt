package io.legado.app.ui.login

import androidx.appcompat.app.AppCompatActivity
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.JsExtensions
import io.legado.app.ui.association.AddToBookshelfDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import java.lang.ref.WeakReference

@Suppress("unused")
class SourceCallbackJsExtensions(
    activity: AppCompatActivity?,
    source: BaseSource?
) : JsExtensions {

    val activityRef: WeakReference<AppCompatActivity> = WeakReference(activity)
    val sourceRef: WeakReference<BaseSource?> = WeakReference(source)

    override fun getSource(): BaseSource? {
        return sourceRef.get()
    }

    fun refreshBookInfo() {
        postEvent(EventBus.REFRESH_BOOK_INFO, true)
    }

    fun refreshBookToc() {
        postEvent(EventBus.REFRESH_BOOK_TOC, true)
    }

    fun refreshContent() {
        postEvent(EventBus.REFRESH_BOOK_CONTENT, true)
    }

    fun searchBook(key: String) {
        activityRef.get()?.let {
            SearchActivity.start(it, key)
        }
    }

    fun addBook(bookUrl: String) {
        activityRef.get()?.showDialogFragment(AddToBookshelfDialog(bookUrl))
    }

}
