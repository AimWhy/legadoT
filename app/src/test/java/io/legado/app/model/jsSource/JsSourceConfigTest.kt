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
    fun exploreUrlArrayNormalizedToJsonString() {
        val s = JsSourceConfig.extract(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "数组发现",
              exploreUrl: [
                { title: "玄幻", url: "/sort/1" },
                { title: "分区头" }
              ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function explore(url, p) { return [] }
            """.trimIndent(),
        )
        // 落库为 JSON 数组字符串,exploreKinds() 原生认该形态;url 可缺省(分区头)
        val kinds = io.legado.app.utils.GSON.fromJson(
            s.exploreUrl, Array<io.legado.app.data.entities.rule.ExploreKind>::class.java
        )
        assertEquals(2, kinds.size)
        assertEquals("玄幻", kinds[0].title)
        assertEquals("/sort/1", kinds[0].url)
        assertEquals("分区头", kinds[1].title)
        assertNull(kinds[1].url)
    }

    @Test
    fun exploreUrlArrayItemMissingTitleFails() {
        assertExtractError(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "坏发现",
              exploreUrl: [ { url: "/sort/1" } ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function explore(url, p) { return [] }
            """.trimIndent(),
            "缺少 title",
        )
    }

    @Test
    fun exploreUrlEmptyArrayFoldsToNoExplore() {
        // 模板留空态:空数组=未声明分类,不触发 explore 配对校验(此脚本未实现 explore)
        val s = JsSourceConfig.extract(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "空发现", exploreUrl: [] }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
        )
        assertNull(s.exploreUrl)
    }

    @Test
    fun exploreUrlArrayStillRequiresExploreFunction() {
        assertExtractError(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "数组缺函数",
              exploreUrl: [ { title: "玄幻", url: "/sort/1" } ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
            "explore",
        )
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

    @Test
    fun loginUiArrayNormalizedToJsonString() {
        val s = JsSourceConfig.extract(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "表单登录",
              loginUi: [
                { name: "账号", type: "text" },
                { name: "密码", type: "password" },
                { name: "发送验证码", type: "button", action: "sendCaptcha(result)" }
              ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function login() { }
            function sendCaptcha(result) { }
            """.trimIndent(),
        )
        // 落库为 JSON 数组字符串,BaseSource.loginUi() 原生认该形态
        val rows = io.legado.app.utils.GSON.fromJson(
            s.loginUi, Array<io.legado.app.data.entities.rule.RowUi>::class.java
        )
        assertEquals(3, rows.size)
        assertEquals("账号", rows[0].name)
        assertEquals("text", rows[0].type)
        assertEquals("password", rows[1].type)
        assertEquals("sendCaptcha(result)", rows[2].action)
    }

    @Test
    fun loginUiEmptyArrayFoldsToNone() {
        // 模板留空态:空数组=未声明,不触发 login 配对校验(此脚本未实现 login)
        val s = JsSourceConfig.extract(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "空表单", loginUi: [] }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
        )
        assertNull(s.loginUi)
    }

    @Test
    fun loginUiStringFormPreserved() {
        val s = JsSourceConfig.extract(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "字符串表单",
              loginUi: '[{"name":"账号","type":"text"}]'
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function login() { }
            """.trimIndent(),
        )
        assertEquals("""[{"name":"账号","type":"text"}]""", s.loginUi)
    }

    @Test
    fun loginUiArrayItemMissingNameFails() {
        assertExtractError(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "坏表单",
              loginUi: [ { type: "text" } ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            function login() { }
            """.trimIndent(),
            "缺少 name",
        )
    }

    @Test
    fun loginUiRequiresLoginFunction() {
        assertExtractError(
            """
            var source = {
              bookSourceUrl: "https://a.com",
              bookSourceName: "缺登录函数",
              loginUi: [ { name: "账号", type: "text" } ]
            }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
            "login",
        )
    }

    @Test
    fun loginUrlAloneNeedsNoLoginFunction() {
        // WebView 登录形态:只声明 loginUrl,无需 login 函数
        val s = JsSourceConfig.extract(
            """
            var source = { bookSourceUrl: "https://a.com", bookSourceName: "网页登录", loginUrl: "https://a.com/login" }
            function search(k, p) { return [] }
            function getChapters(b) { return [] }
            function getContent(c, b) { return "" }
            """.trimIndent(),
        )
        assertEquals("https://a.com/login", s.loginUrl)
    }
}
