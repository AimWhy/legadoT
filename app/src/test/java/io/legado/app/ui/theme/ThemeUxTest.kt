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
}
