package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 主题设置页重设计哨兵(spec 2026-07-27) */
class ThemeSettingsRedesignTest {

    @Test
    fun `paired color preference pairs day night keys and reuses color picker contract`() {
        val src =
            File("src/main/java/io/legado/app/lib/prefs/PairedColorPreference.kt").readText()
        assertTrue(
            "PairedColorPreference 必须直接继承 androidx.preference.Preference",
            src.contains("androidx.preference.Preference"),
        )
        assertTrue("必须经 M3ColorPickerDialog 取色", src.contains("M3ColorPickerDialog.show"))
        assertTrue("requestKey 必须沿用 color_ 前缀约定", src.contains("\"color_"))
        assertTrue("落盘前必须归一化 alpha", src.contains("withAlpha"))
        assertTrue("必须提供 onSaveColor 拦截钩子", src.contains("onSaveColor"))
        assertTrue("必须设置 isSelectable = false", src.contains("isSelectable = false"))

        val attrs = File("src/main/res/values/attrs.xml").readText()
        listOf("PairedColorPreference", "dayKey", "nightKey", "dayDefault", "nightDefault")
            .forEach { assertTrue("attrs.xml 缺少 $it", attrs.contains(it)) }

        val layout =
            File("src/main/res/layout/view_paired_color_preference.xml").readText()
        listOf("tv_paired_title", "card_swatch_day", "tv_swatch_day", "card_swatch_night", "tv_swatch_night")
            .forEach { id -> assertTrue("布局缺少 id: $id", layout.contains(id)) }
    }

    @Test
    fun `duo theme preview reads both day and night key sets directly`() {
        val src =
            File("src/main/java/io/legado/app/lib/prefs/DuoThemePreviewPreference.kt").readText()
        assertTrue(
            "DuoThemePreviewPreference 必须直接继承 androidx.preference.Preference",
            src.contains("androidx.preference.Preference"),
        )
        assertTrue("必须设置 isSelectable = false", src.contains("isSelectable = false"))
        // 双联语义:显式读日/夜两组键,不随当前模式解析
        listOf("cPrimary", "cNPrimary", "cAccent", "cNAccent",
            "cBackground", "cNBackground", "cBBackground", "cNBBackground")
            .forEach { assertTrue("必须直读 PreferKey.$it", src.contains("PreferKey.$it")) }

        val layout = File("src/main/res/layout/view_duo_theme_preview.xml").readText()
        listOf(
            "card_mock_day", "top_bar_day", "accent_day", "bottom_bar_day",
            "card_mock_night", "top_bar_night", "accent_night", "bottom_bar_night",
        ).forEach { id -> assertTrue("布局缺少 id: $id", layout.contains(id)) }
    }

    @Test
    fun `theme color sub page hosts duo preview paired rows bg images and save actions`() {
        val xml = File("src/main/res/xml/pref_config_theme_color.xml").readText()
        assertTrue(xml.contains("io.legado.app.lib.prefs.DuoThemePreviewPreference"))
        assertTrue(xml.contains("io.legado.app.lib.prefs.PairedColorPreference"))
        // 8 色键经 dayKey/nightKey 成对声明,一个不少
        listOf(
            "colorPrimary", "colorPrimaryNight", "colorAccent", "colorAccentNight",
            "colorBackground", "colorBackgroundNight",
            "colorBottomBackground", "colorBottomBackgroundNight",
        ).forEach { key -> assertTrue("子页缺少色键 $key", xml.contains("\"$key\"")) }
        // 背景图与存档迁入
        listOf("backgroundImage", "backgroundImageNight", "saveDayTheme", "saveNightTheme")
            .forEach { key -> assertTrue("子页缺少 key $key", xml.contains("\"$key\"")) }

        val tag = File("src/main/java/io/legado/app/ui/config/ConfigTag.kt").readText()
        assertTrue(tag.contains("THEME_COLOR_CONFIG"))
        val activity = File("src/main/java/io/legado/app/ui/config/ConfigActivity.kt").readText()
        assertTrue(activity.contains("ThemeColorConfigFragment"))

        val fragment =
            File("src/main/java/io/legado/app/ui/config/ThemeColorConfigFragment.kt").readText()
        // 手调回落 + 双联刷新 + 当前模式判定三件套
        assertTrue(fragment.contains("onManualColorChanged"))
        assertTrue(fragment.contains("DuoThemePreviewPreference"))
        assertTrue(fragment.contains("AppConfig.isNightTheme"))
        // 背景图/存档逻辑迁入
        listOf("selectBgAction", "setBgFromUri", "alertImageBlurring", "alertSaveTheme")
            .forEach { fn -> assertTrue("子页 Fragment 缺少 $fn", fragment.contains(fn)) }
        // 明暗校验迁入
        assertTrue(fragment.contains("day_background_too_dark"))
        assertTrue(fragment.contains("night_background_too_light"))
    }
}
