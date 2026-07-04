package io.legado.app.ui.book.search

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.ui.widget.anima.explosion_field.ExplosionField
import splitties.views.onLongClick

class HistoryKeyAdapter(activity: SearchActivity, val callBack: CallBack) :
    RecyclerAdapter<SearchKeyword, ItemFilletTextBinding>(activity) {

    private val explosionField = ExplosionField.attach2Window(activity)

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getViewBinding(parent: ViewGroup): ItemFilletTextBinding {
        return ItemFilletTextBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFilletTextBinding,
        item: SearchKeyword,
        payloads: MutableList<Any>
    ) {
        binding.run {
            textView.text = item.word
            textView.background = filletChipBackground(context)
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFilletTextBinding) {
        holder.itemView.apply {
            setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.searchHistory(it.word)
                }
            }
            onLongClick {
                explosionField.explode(this, true)
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    callBack.deleteHistory(it)
                }
            }
        }
    }

    interface CallBack {
        fun searchHistory(key: String)
        fun deleteHistory(searchKeyword: SearchKeyword)
    }
}

/**
 * 搜索页 chip（历史词/书架匹配）运行时 M3 表面色背景：
 * 默认态 surfaceContainerHighest，按压态 surfaceContainer（更深一档）。
 * 每次绑定新建实例——StateListDrawable 有状态，不可跨 item 复用。
 */
fun filletChipBackground(context: Context): StateListDrawable {
    val radius = context.resources.getDimension(R.dimen.radius_l)
    val scheme = AppColorScheme.current
    fun rounded(color: Int) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(scheme.surfaceContainer))
        addState(intArrayOf(), rounded(scheme.surfaceContainerHighest))
    }
}