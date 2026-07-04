package io.legado.app.ui.rss.article

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.RssArticle
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.gone
import io.legado.app.utils.visible

/**
 * RSS 文章列表 adapter 基类。三种布局风格的 view id 全同、渲染逻辑完全一致,
 * R1 起逻辑单份收敛于此,子类只做 Binding 膨胀与槽位映射（保持 viewbinding 类型安全）。
 */
abstract class BaseRssArticlesAdapter<VB : ViewBinding>(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RssArticle, VB>(context) {

    /** 槽位映射:子类从各自 Binding 取同名视图 */
    protected class ArticleSlots(
        val tvTitle: TextView,
        val tvPubDate: TextView,
        val imageView: ImageView,
    )

    protected abstract fun slots(binding: VB): ArticleSlots

    @SuppressLint("CheckResult")
    final override fun convert(
        holder: ItemViewHolder,
        binding: VB,
        item: RssArticle,
        payloads: MutableList<Any>
    ) {
        val slots = slots(binding)
        slots.tvTitle.text = item.title
        slots.tvPubDate.text = item.pubDate
        if (item.image.isNullOrBlank() && !callBack.isGridLayout) {
            slots.imageView.gone()
        } else {
            val options =
                RequestOptions().set(OkHttpModelLoader.sourceOriginOption, item.origin)
            ImageLoader.load(context, item.image).apply(options).apply {
                if (callBack.isGridLayout) {
                    placeholder(R.drawable.image_rss_article)
                } else {
                    addListener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            slots.imageView.gone()
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            slots.imageView.visible()
                            return false
                        }

                    })
                }
            }.into(slots.imageView)
        }
        if (item.read) {
            slots.tvTitle.setTextColor(context.getCompatColor(R.color.tv_text_summary))
        } else {
            slots.tvTitle.setTextColor(context.getCompatColor(R.color.primaryText))
        }
    }

    final override fun registerListener(holder: ItemViewHolder, binding: VB) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.readRss(it)
            }
        }
    }

    interface CallBack {
        val isGridLayout: Boolean
        fun readRss(rssArticle: RssArticle)
    }
}
