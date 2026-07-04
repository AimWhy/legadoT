package io.legado.app.lib.skin

import androidx.annotation.ColorInt
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.AppSchemeColors
import io.legado.app.lib.theme.ThemeStore
import splitties.init.appCtx

/**
 * 换肤引擎角色枚举:前 32 项与 [AppSchemeColors] 字段同名同序(ordinal=attrs_skin.xml enum value,
 * 三方一致性由 SkinRoleMappingTest 锚定);其后为 app 四色直选角色(经 ThemeStore,非 scheme 派生)。
 * 布局里 skin_* attr 的取值即此处角色名。
 */
enum class SkinRole {
    primary, onPrimary, primaryContainer, onPrimaryContainer,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    error, onError, errorContainer, onErrorContainer,
    outline, outlineVariant, inversePrimary, inverseSurface, inverseOnSurface,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
    surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
    surfaceContainerHigh, surfaceContainerHighest,

    /** 四色·主色(工具栏/标题栏):用户直选原值。注意≠scheme.primary(那是强调色种子的派生) */
    themePrimary,

    /** 四色·底栏色(底部操作面板/导航栏) */
    themeBottomBackground;

    /** 当前主题下的实际色值(AppColorScheme 缓存按主题键自动失效,直读即可) */
    @get:ColorInt
    val color: Int
        get() = resolve(AppColorScheme.current)

    @ColorInt
    fun resolve(scheme: AppSchemeColors): Int = when (this) {
        primary -> scheme.primary
        onPrimary -> scheme.onPrimary
        primaryContainer -> scheme.primaryContainer
        onPrimaryContainer -> scheme.onPrimaryContainer
        secondary -> scheme.secondary
        onSecondary -> scheme.onSecondary
        secondaryContainer -> scheme.secondaryContainer
        onSecondaryContainer -> scheme.onSecondaryContainer
        tertiary -> scheme.tertiary
        onTertiary -> scheme.onTertiary
        tertiaryContainer -> scheme.tertiaryContainer
        onTertiaryContainer -> scheme.onTertiaryContainer
        error -> scheme.error
        onError -> scheme.onError
        errorContainer -> scheme.errorContainer
        onErrorContainer -> scheme.onErrorContainer
        outline -> scheme.outline
        outlineVariant -> scheme.outlineVariant
        inversePrimary -> scheme.inversePrimary
        inverseSurface -> scheme.inverseSurface
        inverseOnSurface -> scheme.inverseOnSurface
        background -> scheme.background
        onBackground -> scheme.onBackground
        surface -> scheme.surface
        onSurface -> scheme.onSurface
        surfaceVariant -> scheme.surfaceVariant
        onSurfaceVariant -> scheme.onSurfaceVariant
        surfaceContainerLowest -> scheme.surfaceContainerLowest
        surfaceContainerLow -> scheme.surfaceContainerLow
        surfaceContainer -> scheme.surfaceContainer
        surfaceContainerHigh -> scheme.surfaceContainerHigh
        surfaceContainerHighest -> scheme.surfaceContainerHighest
        themePrimary -> ThemeStore.primaryColor(appCtx)
        themeBottomBackground -> ThemeStore.bottomBackground(appCtx)
    }

    companion object {
        private val values = entries.toTypedArray()

        /** attr enum value(=ordinal) -> 角色;无效值返回 null(布局层 aapt 已挡,防御分支) */
        fun fromAttrValue(value: Int): SkinRole? = values.getOrNull(value)
    }
}
