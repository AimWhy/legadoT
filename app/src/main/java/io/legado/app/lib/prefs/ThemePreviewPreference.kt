package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import io.legado.app.R
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils

/**
 * N4 主题 UX: 迷你 mock 卡展示当前四色方案。
 *
 * 直接继承 [androidx.preference.Preference](不走 [io.legado.app.lib.prefs.Preference]——
 * 后者的 bindView 硬绑 4 个固定 id,这里的自绘布局用不上),仅设 [layoutResource] 为
 * [R.layout.view_theme_preview],在 [onBindViewHolder] 里读取 ThemeStore 四色现值并反映到布局。
 *
 * 四色语义对位(themePrimary 语义):
 * - 顶栏条 (topBar) = ThemeStore.primaryColor ——强调工具栏/标题栏
 * - 强调钮圆 (accentButton) = ThemeStore.accentColor ——重点交互元素
 * - 背景面 (card_theme_mock) = ThemeStore.backgroundColor ——主要背景
 * - 底栏条 (bottomBar) = ThemeStore.bottomBackground ——底部导航/固定栏
 * - 占位灰条 (placeholderBar) = onSurfaceVariant @ 40% ——中性内容区 (禁用态/次要文字)
 *
 * onBindViewHolder 每次 bind 重读色值(Preference 页在 RECREATE 后重建=即时换色；
 * 同页即时性靠 T5 pref 变更钩子 notifyChanged)。isSelectable=false(纯展示无交互)。
 */
class ThemePreviewPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : androidx.preference.Preference(context, attrs) {

    init {
        layoutResource = R.layout.view_theme_preview
        isSelectable = false
    }

    /**
     * 刷新预览卡(notifyChanged 触发 onBindViewHolder 重装,现读 ThemeStore 四色新值)。
     * 公开包装:androidx.preference.Preference.notifyChanged 本身是 protected,T5 在
     * 手动改色/预设点选后从 Fragment 外部调用需要这层 public 包装才能触达。
     */
    fun refresh() = notifyChanged()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val card = holder.findViewById(R.id.card_theme_mock) as? MaterialCardView ?: return
        val topBar = holder.findViewById(R.id.top_bar) ?: return
        val accentButton = holder.findViewById(R.id.accent_button) ?: return
        val bottomBar = holder.findViewById(R.id.bottom_bar) ?: return
        val placeholderBar1 = holder.findViewById(R.id.placeholder_bar_1) ?: return
        val placeholderBar2 = holder.findViewById(R.id.placeholder_bar_2) ?: return

        // 读取当前四色——themePrimary 语义(非 scheme.primary,而是用户工具栏色)
        val primaryColor = ThemeStore.primaryColor(context)
        val accentColor = ThemeStore.accentColor(context)
        val bgColor = ThemeStore.backgroundColor(context)
        val bottomBgColor = ThemeStore.bottomBackground(context)

        // onSurfaceVariant @ 40% 的中性占位色
        val scheme = AppColorScheme.current
        @ColorInt val variantGray = ColorUtils.withAlpha(scheme.onSurfaceVariant, 0.4f)

        // 设置背景卡面色
        // MaterialCardView 必须走 setCardBackgroundColor——setBackgroundColor 会把渲染圆角的
        // MaterialShapeDrawable 换成方角 ColorDrawable(N3a SkinInflaterFactory 同族坑)
        card.setCardBackgroundColor(bgColor)

        // 顶栏条 = primaryColor (themePrimary 语义)
        topBar.setBackgroundColor(primaryColor)

        // 强调钮圆 = accentColor
        accentButton.setBackgroundColor(accentColor)

        // 底栏条 = bottomBackground
        bottomBar.setBackgroundColor(bottomBgColor)

        // 两行占位灰条 = onSurfaceVariant @ 40%
        placeholderBar1.setBackgroundColor(variantGray)
        placeholderBar2.setBackgroundColor(variantGray)
    }
}
