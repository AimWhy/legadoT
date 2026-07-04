package io.legado.app.ui.rss.article

import android.content.Context
import android.view.ViewGroup
import io.legado.app.databinding.ItemRssArticle2Binding

class RssArticlesAdapter2(context: Context, callBack: CallBack) :
    BaseRssArticlesAdapter<ItemRssArticle2Binding>(context, callBack) {

    override fun getViewBinding(parent: ViewGroup): ItemRssArticle2Binding {
        return ItemRssArticle2Binding.inflate(inflater, parent, false)
    }

    override fun slots(binding: ItemRssArticle2Binding) = ArticleSlots(
        tvTitle = binding.tvTitle,
        tvPubDate = binding.tvPubDate,
        imageView = binding.imageView,
    )
}
