package io.legado.app.ui.book.explore

import android.content.Context
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.visible

/**
 * SearchBook -> item_search 行的通用绑定
 * (发现完整列表 / 发现页列表样式容器共用)
 */
fun ItemSearchBinding.bindSearchBook(context: Context, item: SearchBook, inBookshelf: Boolean) {
    tvName.text = item.name
    tvAuthor.text = context.getString(R.string.author_show, item.author)
    ivInBookshelf.isVisible = inBookshelf
    if (item.latestChapterTitle.isNullOrEmpty()) {
        tvLasted.gone()
    } else {
        tvLasted.text = context.getString(R.string.lasted_show, item.latestChapterTitle)
        tvLasted.visible()
    }
    tvIntroduce.text = item.trimIntro(context)
    val kinds = item.getKindList()
    if (kinds.isEmpty()) {
        llKind.gone()
    } else {
        llKind.visible()
        llKind.setLabels(kinds)
    }
    ivCover.load(
        item.coverUrl,
        item.name,
        item.author,
        AppConfig.loadCoverOnlyWifi,
        item.origin
    )
}
