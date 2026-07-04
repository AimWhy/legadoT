package io.legado.app.ui.main.bookshelf

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ItemBookshelfGridBinding
import io.legado.app.databinding.ItemBookshelfListBinding
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.cardBackgroundColor
import io.legado.app.ui.widget.anima.RotateLoading
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.invisible
import io.legado.app.utils.toTimeAgo

/**
 * 书架封面上"未读角标 + 刷新加载动画"随 Book 状态更新的公共实现，
 * 供 style1/style2 的网格/列表四个 BooksAdapter 共用。
 *
 * @param loadingGone 隐藏加载动画时用 gone()（列表款）还是 inVisible()（网格款，保留占位）
 */
fun upBookBadge(
    bvUnread: BadgeView,
    rlLoading: RotateLoading,
    item: Book,
    isUpdate: Boolean,
    loadingGone: Boolean
) {
    if (!item.isLocal && isUpdate) {
        bvUnread.invisible()
        rlLoading.visible()
    } else {
        if (loadingGone) rlLoading.gone() else rlLoading.inVisible()
        if (AppConfig.showUnread) {
            bvUnread.setBadgeCount(item.getUnreadChapterNum())
            bvUnread.setHighlight(item.lastCheckCount > 0)
        } else {
            bvUnread.invisible()
        }
    }
}

/**
 * 列表款书籍项绑定（全量+payload 增量单份实现,style1/style2 共用）。
 * @param withLastUpdateTime style1 显示"最近更新时间"列,style2 该视图空置
 * @param coverFragment/coverLifecycle 封面 Glide 生命周期宿主(style1 提供)
 */
fun ItemBookshelfListBinding.bindBook(
    item: Book,
    isUpdate: Boolean,
    withLastUpdateTime: Boolean,
    payloads: MutableList<Any>,
    coverFragment: Fragment? = null,
    coverLifecycle: Lifecycle? = null,
) {
    fun loadCover() = ivCover.load(
        item.getDisplayCover(), item.name, item.author, false,
        item.getCoverSourceOrigin(), coverFragment, coverLifecycle
    )

    fun upLastUpdateTime() {
        if (!withLastUpdateTime) return
        if (AppConfig.showLastUpdateTime && !item.isLocal) {
            val time = item.latestChapterTime.toTimeAgo()
            if (tvLastUpdateTime.text != time) {
                tvLastUpdateTime.text = time
            }
        } else {
            tvLastUpdateTime.text = ""
        }
    }

    if (payloads.isEmpty()) {
        tvName.text = item.name
        tvAuthor.text = item.author
        tvRead.text = item.durChapterTitle
        tvLast.text = item.latestChapterTitle
        loadCover()
        upBookBadge(bvUnread, rlLoading, item, isUpdate, loadingGone = true)
        upLastUpdateTime()
    } else {
        for (i in payloads.indices) {
            val bundle = payloads[i] as Bundle
            bundle.keySet().forEach {
                when (it) {
                    "name" -> tvName.text = item.name
                    "author" -> tvAuthor.text = item.author
                    "dur" -> tvRead.text = item.durChapterTitle
                    "last" -> tvLast.text = item.latestChapterTitle
                    "cover" -> loadCover()
                    "refresh" -> upBookBadge(bvUnread, rlLoading, item, isUpdate, loadingGone = true)
                    "lastUpdateTime" -> upLastUpdateTime()
                }
            }
        }
    }
}

/** 网格款书籍项绑定（全量+payload 增量单份实现,style1/style2 共用） */
fun ItemBookshelfGridBinding.bindBook(
    item: Book,
    isUpdate: Boolean,
    payloads: MutableList<Any>,
) {
    coverCard.setCardBackgroundColor(root.context.cardBackgroundColor)

    fun loadCover() = ivCover.load(
        item.getDisplayCover(), item.name, item.author, false, item.getCoverSourceOrigin()
    )

    if (payloads.isEmpty()) {
        tvName.text = item.name
        loadCover()
        upBookBadge(bvUnread, rlLoading, item, isUpdate, loadingGone = false)
    } else {
        for (i in payloads.indices) {
            val bundle = payloads[i] as Bundle
            bundle.keySet().forEach {
                when (it) {
                    "name" -> tvName.text = item.name
                    "cover" -> loadCover()
                    "refresh" -> upBookBadge(bvUnread, rlLoading, item, isUpdate, loadingGone = false)
                }
            }
        }
    }
}
