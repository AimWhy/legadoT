package io.legado.app.ui.rss.article

import android.content.Context
import android.view.ViewGroup
import io.legado.app.databinding.ItemRssArticle1Binding

class RssArticlesAdapter1(context: Context, callBack: CallBack) :
    BaseRssArticlesAdapter<ItemRssArticle1Binding>(context, callBack) {

    override fun getViewBinding(parent: ViewGroup): ItemRssArticle1Binding {
        return ItemRssArticle1Binding.inflate(inflater, parent, false)
    }

    override fun slots(binding: ItemRssArticle1Binding) = ArticleSlots(
        tvTitle = binding.tvTitle,
        tvPubDate = binding.tvPubDate,
        imageView = binding.imageView,
    )
}
