package io.legado.app.ui.rss.article

import android.content.Context
import android.view.ViewGroup
import io.legado.app.databinding.ItemRssArticleBinding

class RssArticlesAdapter(context: Context, callBack: CallBack) :
    BaseRssArticlesAdapter<ItemRssArticleBinding>(context, callBack) {

    override fun getViewBinding(parent: ViewGroup): ItemRssArticleBinding {
        return ItemRssArticleBinding.inflate(inflater, parent, false)
    }

    override fun slots(binding: ItemRssArticleBinding) = ArticleSlots(
        tvTitle = binding.tvTitle,
        tvPubDate = binding.tvPubDate,
        imageView = binding.imageView,
    )
}
