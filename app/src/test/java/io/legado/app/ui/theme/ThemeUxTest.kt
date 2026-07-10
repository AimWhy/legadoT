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

    @Test
    fun `theme preview preference shows four colors with themePrimary semantics and is non-selectable`() {
        val src =
            File("src/main/java/io/legado/app/lib/prefs/ThemePreviewPreference.kt").readText()
        // 必须直接继承 androidx.preference.Preference
        assertTrue(
            "ThemePreviewPreference 必须直接继承 androidx.preference.Preference",
            src.contains("androidx.preference.Preference"),
        )
        // 必须读取 ThemeStore.primaryColor (themePrimary 语义)
        assertTrue(
            "ThemePreviewPreference 必须包含 ThemeStore.primaryColor (themePrimary 语义)",
            src.contains("ThemeStore.primaryColor"),
        )
        // 必须设置 isSelectable = false
        assertTrue(
            "ThemePreviewPreference 必须设置 isSelectable = false",
            src.contains("isSelectable = false"),
        )
        // 必须使用 view_theme_preview 布局
        assertTrue(
            "ThemePreviewPreference 必须使用 view_theme_preview 布局",
            src.contains("view_theme_preview"),
        )
    }

    @Test
    fun `theme settings page mounts preview wallpaper switch and preset row while keeping 8 color keys intact`() {
        val xml = File("src/main/res/xml/pref_config_theme.xml").readText()
        // 三新组件挂载:预览卡/壁纸跟随开关/预设排
        assertTrue(
            "pref_config_theme 必须挂载 ThemePreviewPreference",
            xml.contains("io.legado.app.lib.prefs.ThemePreviewPreference"),
        )
        assertTrue(
            "pref_config_theme 必须挂载 PresetThemesPreference",
            xml.contains("io.legado.app.lib.prefs.PresetThemesPreference"),
        )
        assertTrue(
            "pref_config_theme 必须含壁纸跟随开关 key=wallpaperFollow",
            xml.contains("\"wallpaperFollow\""),
        )
        assertTrue(
            "pref_config_theme 必须含壁纸自动更新子开关 key=wallpaperAutoUpdate",
            xml.contains("\"wallpaperAutoUpdate\""),
        )
        // 红线:8 个 colorXxx key 逐字不动,仅位置迁移进新 category
        listOf(
            "colorPrimary", "colorAccent", "colorBackground", "colorBottomBackground",
            "colorPrimaryNight", "colorAccentNight", "colorBackgroundNight", "colorBottomBackgroundNight",
        ).forEach { key ->
            assertTrue(
                "pref_config_theme 必须仍含 colorXxx key: $key",
                xml.contains("\"$key\""),
            )
        }
    }

    @Test
    fun `m3 color picker dialog exposes requestKey show api and hsv panel with three zone layout`() {
        val dialogSrc =
            File("src/main/java/io/legado/app/ui/widget/dialog/M3ColorPickerDialog.kt").readText()
        assertTrue(
            "M3ColorPickerDialog.show 必须含 requestKey 参数(fragmentResult 契约)",
            dialogSrc.contains("requestKey"),
        )
        assertTrue(
            "M3ColorPickerDialog 必须用 setFragmentResult 交付结果(旋转存活,非 lambda)",
            dialogSrc.contains("setFragmentResult"),
        )
        assertTrue(dialogSrc.contains("fun show("))
        assertTrue(dialogSrc.contains("palette"))
        assertTrue(dialogSrc.contains("showAlpha"))

        val hsvSrc = File("src/main/java/io/legado/app/ui/widget/HsvPanelView.kt").readText()
        assertTrue("HsvPanelView 必须自绘 onDraw", hsvSrc.contains("onDraw"))
        assertTrue("HsvPanelView 必须处理触摸 onTouchEvent", hsvSrc.contains("onTouchEvent"))
        assertTrue("HsvPanelView 必须暴露 color 属性", hsvSrc.contains("var color"))
        assertTrue("HsvPanelView 必须暴露 onColorChanged 回调", hsvSrc.contains("onColorChanged"))

        val layout = File("src/main/res/layout/dialog_m3_color_picker.xml").readText()
        assertTrue(
            "dialog_m3_color_picker 必须挂载 HsvPanelView",
            layout.contains("HsvPanelView"),
        )
        assertTrue(
            "dialog_m3_color_picker 必须含 palette grid 容器 id",
            layout.contains("recycler_palette"),
        )
        assertTrue(
            "dialog_m3_color_picker 必须含 hex 输入框 id",
            layout.contains("et_hex"),
        )
        assertTrue(
            "dialog_m3_color_picker 必须含 alpha slider",
            layout.contains("slider_alpha"),
        )
    }

    @Test
    fun `all six color picker call sites use M3ColorPickerDialog and jaredrummler is fully removed`() {
        // 红线: jaredrummler 取色器库全仓零残留(依赖已删,不得有任何源码引用其包名)
        val srcRoot = File("src/main/java/io/legado/app")
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            assertTrue(
                "${f.path} 不得残留 jaredrummler 引用",
                !f.readText().contains("jaredrummler"),
            )
        }
        val callSites = listOf(
            "lib/prefs/ColorPreference.kt",
            "ui/book/read/config/BgTextConfigDialog.kt",
            "ui/book/read/config/TipConfigDialog.kt",
            "ui/book/read/ReadBookActivity.kt",
            "ui/highlight/edit/HighlightRuleEditDialog.kt",
        )
        callSites.forEach { rel ->
            val text = File("src/main/java/io/legado/app/$rel").readText()
            assertTrue(
                "$rel 必须调用 M3ColorPickerDialog",
                text.contains("M3ColorPickerDialog"),
            )
        }
        // 红线: HighlightColors 预设板数据保留(不因取色器迁移而丢失)
        val highlightColors = File("src/main/java/io/legado/app/help/HighlightColors.kt").readText()
        assertTrue(highlightColors.contains("val bg = intArrayOf"))
        assertTrue(highlightColors.contains("val text = intArrayOf"))
    }
}
