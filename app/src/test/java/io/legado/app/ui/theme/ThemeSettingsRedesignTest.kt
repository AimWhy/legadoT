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

    @Test
    fun `hero preview embeds theme mode segmented group bound to themeMode pref`() {
        val layout = File("src/main/res/layout/view_theme_preview.xml").readText()
        assertTrue(
            "英雄卡必须内嵌 MaterialButtonToggleGroup",
            layout.contains("MaterialButtonToggleGroup"),
        )
        listOf("group_theme_mode", "btn_mode_system", "btn_mode_day", "btn_mode_night", "btn_mode_eink")
            .forEach { id -> assertTrue("布局缺少 id: $id", layout.contains(id)) }
        assertTrue("分段必须单选", layout.contains("singleSelection"))

        val src =
            File("src/main/java/io/legado/app/lib/prefs/ThemePreviewPreference.kt").readText()
        assertTrue("必须绑定 PreferKey.themeMode", src.contains("PreferKey.themeMode"))
        assertTrue("点选必须走 applyDayNight 生效链", src.contains("applyDayNight"))
        assertTrue(
            "文案必须复用 theme_mode 数组(与「我的」页选择器同源)",
            src.contains("R.array.theme_mode"),
        )
    }

    @Test
    fun `main theme page keeps browsing items in three groups and delegates deep color ops`() {
        val xml = File("src/main/res/xml/pref_config_theme.xml").readText()
        // 色项/背景图/存档全部下沉,主页不再承载
        listOf("ColorPreference", "\"colorPrimary\"", "\"backgroundImage\"", "\"saveDayTheme\"", "dayThemeCategory")
            .forEach { probe -> assertTrue("主页不得再含 $probe", !xml.contains(probe)) }
        // 三分组 + 二级页入口
        listOf("colorSchemeCategory", "displayCategory", "elementsCategory", "\"customColorConfig\"")
            .forEach { probe -> assertTrue("主页缺少 $probe", xml.contains(probe)) }
        // 高频项留主页:三沉浸开关+阴影+字号+个性化四件
        listOf(
            "transparentStatusBar", "transparentActionBar", "immNavigationBar",
            "barElevation", "fontScale", "launcherIcon", "welcomeStyle",
            "coverConfig", "bottomBarSkin", "themeList",
        ).forEach { key -> assertTrue("主页缺少 key $key", xml.contains(key)) }

        val fragment =
            File("src/main/java/io/legado/app/ui/config/ThemeConfigFragment.kt").readText()
        assertTrue("入口必须跳 THEME_COLOR_CONFIG", fragment.contains("THEME_COLOR_CONFIG"))
        assertTrue("返回主页须补刷预设排(covers 无 RECREATE 的跨模式手调)", fragment.contains("onResume"))
        // 深度配色逻辑已迁出
        listOf("alertSaveTheme", "setBgFromUri", "selectBgAction", "MenuProvider")
            .forEach { probe -> assertTrue("主页 Fragment 不得再含 $probe", !fragment.contains(probe)) }
        // 日夜快切菜单退役(被分段上位)
        assertTrue(!File("src/main/res/menu/theme_config.xml").exists())
        assertTrue(!fragment.contains("R.menu.theme_config"))
    }
}
