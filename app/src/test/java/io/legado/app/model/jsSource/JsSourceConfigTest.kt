package io.legado.app.model.jsSource

import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsSourceConfigTest {

    private val validScript = """
        var source = {
          bookSourceUrl: "https://example.com",
          bookSourceName: "示例源",
          bookSourceType: 0,
          header: "{\"User-Agent\": \"test\"}"
        }
        function search(key, page) { return [] }
        function getChapters(book) { return [] }
        function getContent(chapter, book) { return "" }
    """.trimIndent()

    private fun assertExtractError(script: String, msgPart: String) {
        try {
            JsSourceConfig.extract(script)
            fail("应当抛出 NoStackTraceException(含 $msgPart)")
        } catch (e: NoStackTraceException) {
            assertTrue("错误消息应含 [$msgPart],实际: ${e.message}", e.message!!.contains(msgPart))
        }
    }

    @Test
    fun extractValidScript() {
        val s = JsSourceConfig.extract(validScript)
        assertEquals("https://example.com", s.bookSourceUrl)
        assertEquals("示例源", s.bookSourceName)
        assertEquals(0, s.bookSourceType)
        assertEquals("{\"User-Agent\": \"test\"}", s.header)  // 任意 BookSource 字段透传
        assertEquals(validScript, s.mainJs)                     // 全文即 mainJs
        assertTrue(s.isJsSource())
    }

    @Test
    fun missingConfigObject() {
        assertExtractError("function search(k, p) { return [] }", "source 配置")
    }

    @Test
    fun missingBookSourceUrl() {
        assertExtractError(
            """
            var source = { bookSourceName: "无url" }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
            "bookSourceUrl",
        )
    }

    @Test
    fun missingRequiredFunction() {
        assertExtractError(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "缺函数" }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            """.trimIndent(),
            "getContent",
        )
    }

    @Test
    fun getBookInfoIsOptional() {
        // validScript 无 getBookInfo,extractValidScript 已证不抛——此处锁语义
        val s = JsSourceConfig.extract(validScript)
        assertTrue(s.isJsSource())
    }

    @Test
    fun declarativeRuleKeysStripped() {
        val s = JsSourceConfig.extract(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "带规则",
              ruleSearch: { bookList: "hack" },
              mainJs: "hack"
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
        )
        assertNull(s.ruleSearch)             // 声明式规则字段被剥离
        assertTrue(s.mainJs!!.contains("function search"))  // mainJs=全文,不被 config 注入
    }

    @Test
    fun exploreUrlRequiresExploreFunction() {
        assertExtractError(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "缺发现", exploreUrl: "玄幻::/sort/1" }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
            "explore",
        )
    }

    @Test
    fun exploreUrlWithExploreFunctionPasses() {
        val s = JsSourceConfig.extract(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "带发现", exploreUrl: "玄幻::/sort/1" }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function explore(url, p) { return [] }
            """.trimIndent(),
        )
        assertEquals("玄幻::/sort/1", s.exploreUrl)
    }

    @Test
    fun stampRewritesNumericLiteralAndKeepsComment() {
        val text = "var source = {\n  lastUpdateTime: 0 // 版本时间戳,写死毫秒数值\n}"
        assertEquals(
            "var source = {\n  lastUpdateTime: 123 // 版本时间戳,写死毫秒数值\n}",
            JsSourceConfig.stampLastUpdateTime(text, 123),
        )
    }

    @Test
    fun stampRewritesDateNowAndQuotedKey() {
        assertEquals(
            "var source = { lastUpdateTime: 123 }",
            JsSourceConfig.stampLastUpdateTime("var source = { lastUpdateTime: Date.now() }", 123),
        )
        assertEquals(
            "var source = { \"lastUpdateTime\": 123 }",
            JsSourceConfig.stampLastUpdateTime("var source = { \"lastUpdateTime\": 0 }", 123),
        )
    }

    @Test
    fun stampOnlyFirstDeclaration() {
        val text = "var source = { lastUpdateTime: 1 }\nvar other = { lastUpdateTime: 2 }"
        assertEquals(
            "var source = { lastUpdateTime: 123 }\nvar other = { lastUpdateTime: 2 }",
            JsSourceConfig.stampLastUpdateTime(text, 123),
        )
    }

    @Test
    fun stampMissingOrExpressionDeclarationReturnsNull() {
        assertNull(JsSourceConfig.stampLastUpdateTime("var source = { bookSourceUrl: \"a\" }", 123))
        assertNull(JsSourceConfig.stampLastUpdateTime("var source = { lastUpdateTime: base + 1 }", 123))
    }

    @Test
    fun topLevelIoFailsCleanly() {
        // 无能力 scope:顶层引用 java 直接失败(安全设计,spec §5)
        assertExtractError(
            """
            var probe = java.ajax("https://evil.com")
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "越权" }
            """.trimIndent(),
            "执行失败",
        )
    }
}
