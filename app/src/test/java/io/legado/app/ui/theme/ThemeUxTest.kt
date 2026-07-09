package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N4 主题 UX 哨兵 */
class ThemeUxTest {
    @Test
    fun `seed applier writes pref keys and goes through applyDayNight`() {
        val src = File("src/main/java/io/legado/app/lib/theme/ThemeSeedApplier.kt").readText()
        assertTrue(src.contains("applyDayNight"))
        assertTrue("红线:不得绕过 applyTheme 直写 ThemeStore", !src.contains("ThemeStore.editTheme"))
        assertTrue(src.contains("fun applySeed"))
    }

    @Test
    fun `preset themes declare 8 chinese entries and preference extends androidx preference`() {
        val presets =
            File("src/main/java/io/legado/app/lib/theme/PresetThemes.kt").readText()
        listOf(
            "daiqing", "zhuyue", "zhusha", "ehuang",
            "songyan", "haitang", "qingci", "moshi",
        ).forEach { id ->
            assertTrue("PresetThemes 缺少预设 id: $id", presets.contains("\"$id\""))
        }
        // PresetThemes.all 恰含 8 个 PresetTheme 条目
        assertTrue(
            "PresetThemes.all 必须恰含 8 个 PresetTheme 条目",
            Regex("PresetTheme\\(").findAll(presets).count() >= 8,
        )
        // 避开 lib.prefs.Preference 的 4-id bindView 硬约束——直接继承 androidx.preference.Preference
        val pref =
            File("src/main/java/io/legado/app/lib/prefs/PresetThemesPreference.kt").readText()
        assertTrue(
            "PresetThemesPreference 必须直接继承 androidx.preference.Preference",
            pref.contains("androidx.preference.Preference"),
        )
        assertTrue(pref.contains("view_preset_themes"))
    }

    @Test
    fun `wallpaper seed gates on sdk and follows color changes only in wallpaper mode`() {
        val src =
            File("src/main/java/io/legado/app/lib/theme/WallpaperSeed.kt").readText()
        // SDK 门:壁纸取色 API 均需 12+(S=31)
        assertTrue(
            "WallpaperSeed 必须含 SDK_INT 门(Build.VERSION_CODES.S)",
            src.contains("Build.VERSION.SDK_INT") && src.contains("Build.VERSION_CODES.S"),
        )
        // autoUpdate 注册壁纸颜色变化监听
        assertTrue(
            "WallpaperSeed 必须注册 addOnColorsChangedListener",
            src.contains("addOnColorsChangedListener"),
        )
        // 回调守卫:仅当仍处 wallpaper 模式才重新取色,防止用户切走后被旧监听拉回
        assertTrue(
            "监听回调必须以 themeSeedMode==\"wallpaper\" 守卫",
            src.contains("\"wallpaper\""),
        )
        // 契约:取不到种子回 false 由调用方处理(toast+回滚)
        assertTrue(src.contains("fun setFollow"))
        assertTrue(src.contains("fun currentSeed"))
        assertTrue(src.contains("fun isAvailable"))
    }
}
