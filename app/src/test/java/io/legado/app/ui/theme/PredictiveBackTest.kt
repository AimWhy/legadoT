package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 预测式返回哨兵:manifest 开关在+BaseActivity 不得有压制预测动画的无条件兜底回调 */
class PredictiveBackTest {

    @Test
    fun `manifest opts in and base activity has no blanket callback`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:enableOnBackInvokedCallback=\"true\""))
        val base = File("src/main/java/io/legado/app/base/BaseActivity.kt").readText()
        assertTrue(
            "BaseActivity 的无条件 finish 兜底会让所有普通页失去系统预测动画",
            !base.contains("onBackPressedDispatcher.addCallback")
        )
    }
}
