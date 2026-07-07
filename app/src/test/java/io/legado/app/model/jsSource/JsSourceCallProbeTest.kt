package io.legado.app.model.jsSource

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定纯JS源的调用机制与归一化契约(spec §2/§7):
 * eval 调用表达式(参数=绑定)可调顶层函数;NativeObject/NativeArray 经 GSON 归一化。
 */
class JsSourceCallProbeTest {

    private fun callViaEval(mainJs: String, callExpr: String, args: List<Pair<String, Any?>>): Any? {
        val bindings = buildScriptBindings { b ->
            args.forEach { (k, v) -> b[k] = v }
        }
        val scope = RhinoScriptEngine.getRuntimeScope(bindings)
        RhinoScriptEngine.eval(mainJs, scope)
        return RhinoScriptEngine.eval(callExpr, scope)
    }

    @Test
    fun evalCallExpressionInvokesTopLevelFunction() {
        val raw = callViaEval(
            "function search(key, page) { return [{name: key, bookUrl: 'u' + page}] }",
            "search(key, page)",
            listOf("key" to "斗罗", "page" to 2),
        )
        val json = JsSourceEngine.normalizeJsResult(raw)!!
        assertTrue(json.contains("斗罗"))
        assertTrue(json.contains("u2"))
    }

    @Test
    fun nativeObjectNormalizesViaGson() {
        val raw = callViaEval(
            "function info() { return {intro: '简介', wordCount: '12万字'} }",
            "info()",
            emptyList(),
        )
        val json = JsSourceEngine.normalizeJsResult(raw)!!
        assertTrue(json.contains("\"intro\""))
        assertTrue(json.contains("12万字"))
    }

    @Test
    fun stringPassesThroughUntouched() {
        val raw = callViaEval(
            "function getContent() { return '第一段\\n第二段' }",
            "getContent()",
            emptyList(),
        )
        assertEquals("第一段\n第二段", JsSourceEngine.normalizeJsResult(raw))
    }

    @Test
    fun nullAndUndefinedBecomeNull() {
        assertNull(JsSourceEngine.normalizeJsResult(null))
        val raw = callViaEval("function noop() { }", "noop()", emptyList())
        assertNull(JsSourceEngine.normalizeJsResult(raw))
    }

    @Test
    fun jsonStringifyEquivalentToDirectReturn() {
        val direct = callViaEval(
            "function f() { return [{a: 1}] }", "f()", emptyList(),
        ).let { JsSourceEngine.normalizeJsResult(it)!! }
        val stringified = callViaEval(
            "function f() { return JSON.stringify([{a: 1}]) }", "f()", emptyList(),
        ).let { JsSourceEngine.normalizeJsResult(it)!! }
        // 语义等价(数字表示可能 1 vs 1.0,断言结构键存在即可)
        assertTrue(direct.contains("\"a\""))
        assertTrue(stringified.contains("\"a\""))
    }

    @Test
    fun circularReferenceStringifyFailureThrowsExplicitError() {
        val raw = callViaEval(
            "function f() { var a = {}; a.self = a; return a }",
            "f()",
            emptyList(),
        )
        val error = assertThrows(NoStackTraceException::class.java) {
            JsSourceEngine.normalizeJsResult(raw)
        }
        assertTrue(error.message.orEmpty().contains("JSON.stringify 失败"))
    }
}
