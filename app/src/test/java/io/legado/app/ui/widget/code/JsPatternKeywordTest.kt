package io.legado.app.ui.widget.code

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JsPatternKeywordTest {

    private val ext =
        File("src/main/java/io/legado/app/ui/widget/code/CodeViewExtensions.kt").readText()

    @Test
    fun jsPatternCoversCoreKeywords() {
        // 锁定关键字集升级:J1 之前 jsPattern 只高亮 var
        listOf("function", "return", "typeof", "instanceof").forEach {
            assertTrue("jsPattern 应含关键字 $it", ext.contains(it))
        }
    }
}
