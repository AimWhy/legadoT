package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getPrefInt

/**
 * 日夜双联预览:并排两个迷你 mock,分别直读日键组/夜键组,不随当前显示模式解析——
 * 白天调夜间色也能即时看到落位。展示的是"存储的四色配置";e-ink 的黑白压制发生在
 * 主题应用层,不反映在此卡(页面顶层的模式分段已表达 e-ink 状态)。
 *
 * 直接继承 [androidx.preference.Preference](ThemePreviewPreference 先例),
 * 布局 [R.layout.view_duo_theme_preview],isSelectable=false 纯展示。
 * 四色语义对位与 ThemePreviewPreference 一致(顶栏=themePrimary 语义)。
 */
class DuoThemePreviewPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : androidx.preference.Preference(context, attrs) {

    init {
        layoutResource = R.layout.view_duo_theme_preview
        isSelectable = false
    }

    fun refresh() = notifyChanged()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val gray = ColorUtils.withAlpha(AppColorScheme.current.onSurfaceVariant, 0.4f)
        bindMock(
            holder,
            MockIds(
                R.id.card_mock_day, R.id.top_bar_day, R.id.accent_day, R.id.bottom_bar_day,
                R.id.placeholder_day_1, R.id.placeholder_day_2,
            ),
            primary = context.getPrefInt(
                PreferKey.cPrimary, ContextCompat.getColor(context, R.color.md_theme_day_primary)
            ),
            accent = context.getPrefInt(
                PreferKey.cAccent, ContextCompat.getColor(context, R.color.md_theme_day_accent)
            ),
            background = context.getPrefInt(
                PreferKey.cBackground,
                ContextCompat.getColor(context, R.color.md_theme_day_background)
            ),
            bottomBackground = context.getPrefInt(
                PreferKey.cBBackground,
                ContextCompat.getColor(context, R.color.md_theme_day_bottom_background)
            ),
            gray = gray,
        )
        bindMock(
            holder,
            MockIds(
                R.id.card_mock_night, R.id.top_bar_night, R.id.accent_night,
                R.id.bottom_bar_night, R.id.placeholder_night_1, R.id.placeholder_night_2,
            ),
            primary = context.getPrefInt(
                PreferKey.cNPrimary,
                ContextCompat.getColor(context, R.color.md_theme_night_primary)
            ),
            accent = context.getPrefInt(
                PreferKey.cNAccent, ContextCompat.getColor(context, R.color.md_theme_night_accent)
            ),
            background = context.getPrefInt(
                PreferKey.cNBackground,
                ContextCompat.getColor(context, R.color.md_theme_night_background)
            ),
            bottomBackground = context.getPrefInt(
                PreferKey.cNBBackground,
                ContextCompat.getColor(context, R.color.md_theme_night_bottom_background)
            ),
            gray = gray,
        )
    }

    private class MockIds(
        val card: Int,
        val topBar: Int,
        val accent: Int,
        val bottomBar: Int,
        val placeholder1: Int,
        val placeholder2: Int,
    )

    private fun bindMock(
        holder: PreferenceViewHolder,
        ids: MockIds,
        primary: Int,
        accent: Int,
        background: Int,
        bottomBackground: Int,
        gray: Int,
    ) {
        val card = holder.findViewById(ids.card) as? MaterialCardView ?: return
        // MaterialCardView 必须走 setCardBackgroundColor 保住圆角 shape
        card.setCardBackgroundColor(background)
        holder.findViewById(ids.topBar)?.setBackgroundColor(primary)
        holder.findViewById(ids.accent)?.setBackgroundColor(accent)
        holder.findViewById(ids.bottomBar)?.setBackgroundColor(bottomBackground)
        holder.findViewById(ids.placeholder1)?.setBackgroundColor(gray)
        holder.findViewById(ids.placeholder2)?.setBackgroundColor(gray)
    }
}
