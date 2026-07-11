package io.legado.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** N5 长尾收官 Wave A 清尸哨兵 */
class N5LongtailTest {
    @Test
    fun `vertical seekbar family retired`() {
        assertFalse("VerticalSeekBar.kt 应删除",
            File("src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBar.kt").exists())
        assertFalse("VerticalSeekBarWrapper.kt 应删除",
            File("src/main/java/io/legado/app/ui/widget/seekbar/VerticalSeekBarWrapper.kt").exists())
        val attrs = File("src/main/res/values/attrs.xml").readText()
        assertFalse("VerticalSeekBar styleable 应删除", attrs.contains("name=\"VerticalSeekBar\""))
    }
}
