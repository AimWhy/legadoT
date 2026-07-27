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
}
