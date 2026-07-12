package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemPresetThemeBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.PresetTheme
import io.legado.app.lib.theme.PresetThemes
import io.legado.app.lib.theme.ThemeSeedApplier

/**
 * N4 主题 UX:中式意象预设色块横滑排。
 *
 * 直接继承 [androidx.preference.Preference](不走 [io.legado.app.lib.prefs.Preference]——
 * 后者的 bindView 硬绑 4 个固定 id,这里的自绘布局用不上),仅设 [layoutResource] 为
 * [R.layout.view_preset_themes],在 [onBindViewHolder] 里把横向 RecyclerView 装上
 * [PresetThemes.all] 的适配器。
 *
 * 施色单轨:卡面颜色一律经 [AppColorScheme.ambientScheme] 由种子现场派生(day 底/primary 圆点 +
 * night primary 点缀小圆),选中态(themeSeedMode=="preset:$id")用 MaterialCardView 描边强调。
 */
class PresetThemesPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : androidx.preference.Preference(context, attrs) {

    init {
        layoutResource = R.layout.view_preset_themes
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val recycler = holder.findViewById(R.id.recycler_preset) as? RecyclerView ?: return
        val lm = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recycler.layoutManager = lm
        val adapter = PresetAdapter(context)
        recycler.adapter = adapter
        adapter.setItems(PresetThemes.all)
        // 点选预设/手动改色会 postEvent(RECREATE) 令 ConfigActivity.recreate() 重建整个设置页,
        // 横滑列表随之从头重装。用进程级静态位置(扛得住 recreate)恢复上次横滑处,避免"点完跳回开头"。
        lm.scrollToPositionWithOffset(savedPosition, savedOffset)
        recycler.clearOnScrollListeners()
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != RecyclerView.NO_POSITION) {
                    savedPosition = pos
                    savedOffset = lm.findViewByPosition(pos)?.left ?: 0
                }
            }
        })
    }

    /**
     * 刷新选中态描边(notifyChanged 触发 onBindViewHolder 重装)。
     * 公开:除内部点选自刷新外,T5 在手动改色回落钩子(ThemeConfig.onManualColorChanged)
     * 触发后从 Fragment 外部调用,取消预设排的选中描边(androidx.preference.Preference.notifyChanged
     * 本身是 protected,子类需要这层 public 包装才能被外部调用方触达)。
     */
    fun refresh() = notifyChanged()

    companion object {
        // 横滑位置(首个可见项索引 + 其左偏移),进程级静态——跨 ConfigActivity.recreate() 存活,
        // 令选预设/改色触发重建后横滑列表恢复原处(见 onBindViewHolder)。进程死亡后回 0,可接受。
        private var savedPosition = 0
        private var savedOffset = 0
    }

    private class PresetAdapter(
        context: Context,
    ) : RecyclerAdapter<PresetTheme, ItemPresetThemeBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemPresetThemeBinding =
            ItemPresetThemeBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemPresetThemeBinding,
            item: PresetTheme,
            payloads: MutableList<Any>,
        ) = binding.run {
            val day = AppColorScheme.ambientScheme(item.seed, isDark = false)
            val night = AppColorScheme.ambientScheme(item.seed, isDark = true)
            colorBlock.setBackgroundColor(day.primaryContainer)
            dotPrimary.setImageDrawable(ColorDrawable(day.primary))
            dotAccent.setImageDrawable(ColorDrawable(night.primary))
            tvPresetName.text = context.getString(item.nameRes)
            val selected = ThemeConfig.themeSeedMode == "preset:${item.id}"
            cardPreset.strokeColor = if (selected) day.primary else Color.TRANSPARENT
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemPresetThemeBinding) {
            binding.cardPreset.setOnClickListener {
                val item = getItem(holder.layoutPosition) ?: return@setOnClickListener
                // 只 applySeed:它必然 postEvent(RECREATE) 重建整页,recreate 时 convert 重读
                // themeSeedMode 自动重画选中描边。不再额外 notifyChanged——那会在 recreate 之外
                // 多触发一次列表重装+滚动恢复,造成"咔咔挪两下"。
                ThemeSeedApplier.applySeed(context, item.seed, "preset:${item.id}")
            }
        }
    }
}
