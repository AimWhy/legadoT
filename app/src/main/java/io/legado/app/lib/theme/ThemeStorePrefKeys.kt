package io.legado.app.lib.theme

/**
 * 四色键 + prefs 文件名。历史安装可能残留 ATH 时代的孤儿键(primary_color_dark 等),无害。
 *
 * @author Aidan Follestad (afollestad), Karim Abou Zeid (kabouzeid)
 */
object ThemeStorePrefKeys {

    const val CONFIG_PREFS_KEY_DEFAULT = "app_themes"

    const val KEY_PRIMARY_COLOR = "primary_color"
    const val KEY_ACCENT_COLOR = "accent_color"
    const val KEY_BACKGROUND_COLOR = "backgroundColor"
    const val KEY_BOTTOM_BACKGROUND = "bottomBackground"
}
