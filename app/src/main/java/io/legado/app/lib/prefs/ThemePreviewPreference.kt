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
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

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
 * 卡底分段绑 themeMode(值序同 @array/theme_mode_v:0 跟随系统/1 日间/2 夜间/3 E-Ink),点选写 pref 后
 * post applyDayNight——镜像「我的」页 NameListPreference 链路。
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

        (holder.findViewById(R.id.group_theme_mode) as? MaterialButtonToggleGroup)
            ?.let { bindThemeModeGroup(it) }
    }

    private fun bindThemeModeGroup(group: MaterialButtonToggleGroup) {
        val buttonIds = listOf(
            R.id.btn_mode_system, R.id.btn_mode_day, R.id.btn_mode_night, R.id.btn_mode_eink
        )
        val labels = context.resources.getStringArray(R.array.theme_mode)
        buttonIds.forEachIndexed { index, id ->
            group.findViewById<MaterialButton>(id)?.text = labels.getOrElse(index) { "" }
        }
        applyGroupTint(group)
        // 先清监听再设选中:notifyChanged 重绑与程序化 check 都会触发监听,不设防会自递归
        group.clearOnButtonCheckedListeners()
        val current = (context.getPrefString(PreferKey.themeMode, "0") ?: "0")
            .toIntOrNull()?.coerceIn(0, 3) ?: 0
        group.check(buttonIds[current])
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val value = buttonIds.indexOf(checkedId).toString()
            if (value == (context.getPrefString(PreferKey.themeMode, "0") ?: "0")) {
                return@addOnButtonCheckedListener
            }
            context.putPrefString(PreferKey.themeMode, value)
            group.post { ThemeConfig.applyDayNight(context) }
        }
    }

    /**
     * 分段施色:选中=强调色 18% 底+强调色字,未选=透明底+onSurfaceVariant 字。
     * 状态感知 CSL 一次装配,勾选切换由状态机自取色,无需在监听里重刷。
     */
    private fun applyGroupTint(group: MaterialButtonToggleGroup) {
        val accent = context.accentColor
        val scheme = AppColorScheme.current
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        )
        val bgCsl = ColorStateList(
            states,
            intArrayOf(ColorUtils.withAlpha(accent, 0.18f), Color.TRANSPARENT),
        )
        val textCsl = ColorStateList(states, intArrayOf(accent, scheme.onSurfaceVariant))
        val strokeCsl =
            ColorStateList.valueOf(ColorUtils.withAlpha(scheme.onSurfaceVariant, 0.35f))
        group.children.filterIsInstance<MaterialButton>().forEach { button ->
            button.backgroundTintList = bgCsl
            button.setTextColor(textCsl)
            button.strokeColor = strokeCsl
        }
    }
}
