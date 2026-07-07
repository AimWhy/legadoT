package io.legado.app.model.jsSource

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 哨兵:WebBook 四入口必须在声明式骨架之前分派 JS 源(spec §5)。
 * 单测工作目录 = app 模块根。
 */
class JsSourceDispatchSentinelTest {

    private val webBook =
        File("src/main/java/io/legado/app/model/webBook/WebBook.kt").readText()

    @Test
    fun fourAwaitEntriesDispatchToJsSourceBook() {
        listOf(
            "JsSourceBook.searchAwait",
            "JsSourceBook.getBookInfoAwait",
            "JsSourceBook.getChapterListAwait",
            "JsSourceBook.getContentAwait",
        ).forEach {
            assertTrue("WebBook 缺少分派: $it", webBook.contains(it))
        }
    }

    @Test
    fun searchDispatchPrecedesDeclarativeGuard() {
        val dispatch = webBook.indexOf("JsSourceBook.searchAwait")
        val declarativeGuard = webBook.indexOf("搜索url不能为空")
        assertTrue("JS 分派必须在 searchUrl 空判之前", dispatch in 1 until declarativeGuard)
    }
}
