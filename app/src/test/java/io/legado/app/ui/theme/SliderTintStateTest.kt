package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Slider 禁用态可见性哨兵（真机验收回归实锤：状态不变的单色 tint 把门控禁用的滑杆
 * 渲染得与启用态一模一样，用户把"亮度跟随/跟随系统语速开启时的禁用"误读成"滑杆坏了"）。
 * 约定：所有 Slider 施色一律走 utils/SliderExtensions.applyAppTint（双态 ColorStateList，
 * 禁用=降透明），任何文件不得再手写 thumbTintList 单色赋值。
 */
class SliderTintStateTest {

    /** 全文件禁手写 thumbTintList 的五处（文件内无其它 thumb 类控件） */
    private val sliderOnlySites = listOf(
        "src/main/java/io/legado/app/ui/book/read/config/BgTextConfigDialog.kt",
        "src/main/java/io/legado/app/ui/book/read/config/ReadAloudDialog.kt",
        "src/main/java/io/legado/app/ui/book/read/MangaMenu.kt",
        "src/main/java/io/legado/app/ui/book/read/ReadMenu.kt",
        "src/main/java/io/legado/app/ui/widget/DetailSeekBar.kt",
    )

    @Test
    fun `shared slider tint extension is state aware`() {
        val ext = File("src/main/java/io/legado/app/utils/SliderExtensions.kt").readText()
        assertTrue("扩展必须存在且含禁用态状态数组", ext.contains("-android.R.attr.state_enabled"))
        assertTrue(ext.contains("fun Slider.applyAppTint"))
    }

    @Test
    fun `all slider tint sites route through applyAppTint`() {
        sliderOnlySites.forEach { path ->
            val src = File(path).readText()
            assertTrue("$path 应改走 applyAppTint", src.contains("applyAppTint("))
            assertTrue(
                "$path 不得手写 slider thumbTintList（状态不变单色会掩蔽禁用态）",
                !src.contains("thumbTintList")
            )
        }
        // 引擎文件含 SwitchCompat 的合法 thumbTintList,检查收窄到 applySliderTint 函数体
        val engine = File("src/main/java/io/legado/app/lib/skin/SkinInflaterFactory.kt").readText()
        val body = engine.substringAfter("private fun applySliderTint").substringBefore("}")
        assertTrue("引擎 Slider 分支应改走 applyAppTint", body.contains("applyAppTint("))
        assertTrue("引擎 applySliderTint 不得手写 thumbTintList", !body.contains("thumbTintList"))
    }
}
