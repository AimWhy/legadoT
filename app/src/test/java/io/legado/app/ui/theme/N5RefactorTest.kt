package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N5 Wave C 重构收敛哨兵(行为等价,验证共享物+去重) */
class N5RefactorTest {
    @Test
    fun `detail seekbar buttons share step helper`() {
        val src = File("src/main/java/io/legado/app/ui/widget/DetailSeekBar.kt").readText()
        assertTrue("应抽出 step 私有方法", src.contains("private fun step("))
        // 两钮都调 step,不再各自内联 coerceIn
        assertTrue("加钮调 step", src.contains("step(1)"))
        assertTrue("减钮调 step", src.contains("step(-1)"))
    }
}
