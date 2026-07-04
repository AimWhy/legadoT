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
 * (发现完整列表 / 发现页列表样式容器 / 搜索结果共用;搜索页的源数角标由调用方另补)
 */
fun ItemSearchBinding.bindSearchBook(context: Context, item: SearchBook, inBookshelf: Boolean) {
    tvName.text = item.name
    tvAuthor.text = context.getString(R.string.author_show, item.author)
    ivInBookshelf.isVisible = inBookshelf
    upLasted(context, item.latestChapterTitle)
    tvIntroduce.text = item.trimIntro(context)
    upKind(item.getKindList())
    ivCover.load(
        item.coverUrl,
        item.name,
        item.author,
        AppConfig.loadCoverOnlyWifi,
        item.origin
    )
}

/** 最新章节行:空则整行隐藏(增量 payload 路径亦可单独调用) */
fun ItemSearchBinding.upLasted(context: Context, latestChapterTitle: String?) {
    if (latestChapterTitle.isNullOrEmpty()) {
        tvLasted.gone()
    } else {
        tvLasted.text = context.getString(R.string.lasted_show, latestChapterTitle)
        tvLasted.visible()
    }
}

/** 分类标签流:空则隐藏(增量 payload 路径亦可单独调用) */
fun ItemSearchBinding.upKind(kinds: List<String>) {
    if (kinds.isEmpty()) {
        llKind.gone()
    } else {
        llKind.visible()
        llKind.setLabels(kinds)
    }
}
