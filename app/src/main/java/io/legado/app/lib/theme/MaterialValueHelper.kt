@file:Suppress("unused")

package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import kotlin.math.pow

/**
 * @author Karim Abou Zeid (kabouzeid)
 */

/**
 * 判断操作栏“实际可见”的背景是否为浅色，用来决定文字/图标取深色还是浅色前景。
 * 沉浸式（透明）操作栏时，看到的是页面内容背景，而非栏自身的主色/底栏色，
 * 因此必须用内容背景判断，否则浅背景配白字会看不清。
 * 返回值直接喂给 [getPrimaryTextColor] / [getSecondaryTextColor]（入参即 isLight）。
 */
fun appBarBackgroundIsLight(
    transparentActionBar: Boolean,
    @ColorInt barBackgroundColor: Int,
    @ColorInt contentBackgroundColor: Int,
): Boolean {
    val effectiveBackground = if (transparentActionBar) {
        contentBackgroundColor
    } else {
        barBackgroundColor
    }
    return isColorLight(effectiveBackground)
}

private fun isColorLight(@ColorInt color: Int): Boolean {
    return linearChannel(color shr 16 and 0xFF) * 0.2126 +
            linearChannel(color shr 8 and 0xFF) * 0.7152 +
            linearChannel(color and 0xFF) * 0.0722 >= 0.5
}

private fun linearChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

/**
 * TitleBar/TabLayout 标签文字色：选中=不透明纯黑/白（最醒目），未选中=半透明（弱化）。
 * 选中靠“实心 + 指示器”区分，不靠强调色，避免强调色亮度接近未选中导致主次颠倒。
 * @param barIsLight 标签所在栏背景是否为浅色（浅底用黑系，深底用白系）。
 */
fun tabTextColors(barIsLight: Boolean): TabTextColors {
    return if (barIsLight) {
        TabTextColors(selected = 0xFF000000.toInt(), unselected = 0x8A000000.toInt())
    } else {
        TabTextColors(selected = 0xFFFFFFFF.toInt(), unselected = 0xB3FFFFFF.toInt())
    }
}

data class TabTextColors(
    @get:ColorInt val selected: Int,
    @get:ColorInt val unselected: Int,
)

@ColorInt
fun Context.getPrimaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_primary_text)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getSecondaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_secondary)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getPrimaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    }
}

@ColorInt
fun Context.getSecondaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_light
        )
    } else {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_dark
        )
    }
}

val Context.primaryColor: Int
    get() = ThemeStore.primaryColor(this)

val Context.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(this)

val Context.accentColor: Int
    get() = ThemeStore.accentColor(this)

val Context.backgroundColor: Int
    get() = ThemeStore.backgroundColor(this)

val Context.bottomBackground: Int
    get() = ThemeStore.bottomBackground(this)

val Context.primaryTextColor: Int
    get() = getPrimaryTextColor(isDarkTheme)

/**
 * 工具栏文字色：沉浸式时按页面背景判断明暗，非沉浸时按主色判断。
 * 供位于 TitleBar 内的 SearchView、菜单文字等使用，替代直接取 [primaryTextColor]。
 */
val Context.toolbarTextColor: Int
    get() = getPrimaryTextColor(
        appBarBackgroundIsLight(
            transparentActionBar = AppConfig.isTransparentActionBar,
            barBackgroundColor = primaryColor,
            contentBackgroundColor = backgroundColor
        )
    )

val Context.secondaryTextColor: Int
    get() = getSecondaryTextColor(isDarkTheme)

val Context.primaryDisabledTextColor: Int
    get() = getPrimaryDisabledTextColor(isDarkTheme)

val Context.secondaryDisabledTextColor: Int
    get() = getSecondaryDisabledTextColor(isDarkTheme)

val Fragment.primaryColor: Int
    get() = ThemeStore.primaryColor(requireContext())

val Fragment.toolbarTextColor: Int
    get() = requireContext().toolbarTextColor

val Fragment.primaryColorDark: Int
    get() = ThemeStore.primaryColorDark(requireContext())

val Fragment.accentColor: Int
    get() = ThemeStore.accentColor(requireContext())

val Fragment.backgroundColor: Int
    get() = ThemeStore.backgroundColor(requireContext())

val Fragment.bottomBackground: Int
    get() = ThemeStore.bottomBackground(requireContext())

val Fragment.primaryTextColor: Int
    get() = requireContext().getPrimaryTextColor(isDarkTheme)

val Fragment.secondaryTextColor: Int
    get() = requireContext().getSecondaryTextColor(isDarkTheme)

val Fragment.primaryDisabledTextColor: Int
    get() = requireContext().getPrimaryDisabledTextColor(isDarkTheme)

val Fragment.secondaryDisabledTextColor: Int
    get() = requireContext().getSecondaryDisabledTextColor(isDarkTheme)

val Context.buttonDisabledColor: Int
    get() = if (isDarkTheme) {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    }

val Context.isDarkTheme: Boolean
    get() = ColorUtils.isColorLight(ThemeStore.primaryColor(this))

val Fragment.isDarkTheme: Boolean
    get() = requireContext().isDarkTheme

val Context.elevation: Float
    @SuppressLint("PrivateResource")
    get() {
        return if (AppConfig.elevation < 0) {
            ThemeUtils.resolveFloat(
                this,
                android.R.attr.elevation,
                resources.getDimension(com.google.android.material.R.dimen.design_appbar_elevation)
            )
        } else {
            AppConfig.elevation.toFloat().dpToPx()
        }
    }

val Context.filletBackground: GradientDrawable
    get() {
        val background = GradientDrawable()
        background.cornerRadius = 3f.dpToPx()
        background.setColor(backgroundColor)
        return background
    }

/**
 * 卡片背景色：沉浸式操作栏/状态栏时完全透明，列表行直接浮在页面背景上；
 * 非沉浸式时保持纯色不变。
 */
val Context.cardBackgroundColor: Int
    get() {
        val immersive = AppConfig.isTransparentStatusBar || AppConfig.isTransparentActionBar
        if (!immersive) {
            return ContextCompat.getColor(this, R.color.background_card)
        }
        return android.graphics.Color.TRANSPARENT
    }