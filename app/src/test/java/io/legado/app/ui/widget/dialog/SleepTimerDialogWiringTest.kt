package io.legado.app.ui.widget.dialog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 停止设置弹窗形态锚点:浮动弹窗模板、时间/集数预设等数、bottom sheet 通路退场。
 */
class SleepTimerDialogWiringTest {

    @Test
    fun `sleep timer dialog is floating template not bottom sheet`() {
        val src = readProjectFile("src/main/java/io/legado/app/ui/widget/dialog/SleepTimerDialog.kt")
        assertTrue(
            "应继承 BaseDialogFragment 走浮动模板",
            src.contains("BaseDialogFragment(R.layout.dialog_sleep_timer)")
        )
        assertFalse("BottomSheet 通路应退场", src.contains("BottomSheetDialogFragment"))
        assertFalse("sheet 容器涂色应随形态退场", src.contains("applyAppSheetBackground"))
        // 时间/集数预设等数(两行等宽对齐的前提)
        val time = Regex("""TIME_PRESETS = intArrayOf\(([^)]*)\)""")
            .find(src)!!.groupValues[1].split(",").size
        val chapter = Regex("""CHAPTER_PRESETS = intArrayOf\(([^)]*)\)""")
            .find(src)!!.groupValues[1].split(",").size
        assertTrue("两段预设数量须一致(time=$time chapter=$chapter)", time == chapter)
        assertTrue("预设应为 4 个", time == 4)
    }

    @Test
    fun `sleep timer layout drops bottom sheet artifacts and aligns chip rows`() {
        val layout = readProjectFile("src/main/res/layout/dialog_sleep_timer.xml")
        assertFalse("drag handle 应退场", layout.contains("BottomSheetDragHandleView"))
        assertFalse("Flexbox 应换等权重行", layout.contains("FlexboxLayout"))
        val weights = Regex("""android:layout_weight="1"""").findAll(layout).count()
        assertTrue("等宽 chip 行缺失(layout_weight=1 计数 $weights, 期望≥10)", weights >= 10)
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.first { it.isFile }.readText()
    }
}
