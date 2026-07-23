package io.legado.app.model.login

import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.model.jsSource.JsSourceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 eval 装配的引擎探针:脚本形态与 BaseSource.evalLoginUiV2/evalLoginActionV2 一致,
 * 锚定 JSON 字符串跨边界往返(Java 包装字符串先 String() 归一化)与 normalizeJsResult 归一化。
 */
class LoginUiV2EngineTest {

    private fun eval(script: String, vararg binds: Pair<String, Any?>): String? {
        val bindings = buildScriptBindings { b -> binds.forEach { (k, v) -> b[k] = v } }
        val scope = RhinoScriptEngine.getRuntimeScope(bindings)
        return JsSourceEngine.normalizeJsResult(RhinoScriptEngine.eval(script, scope))
    }

    private val sourceJs = """
        function loginUi(state) {
          if (!state.step) return { rows: [
            { key: "phone", name: "手机号", type: "text" },
            { name: "发送验证码", type: "button", action: "sendCode" }
          ]};
          return { rows: [
            { key: "code", name: "验证码", type: "text" },
            { name: "重新发码", type: "button", action: "sendCode", countdown: 60 }
          ]};
        }
        function loginAction(action, state, form) {
          if (action == "sendCode") {
            if (!form.phone) return { error: { phone: "手机号必填" } };
            return { state: { step: "code", phone: `+86${'$'}{form.phone}` } };
          }
          if (action == "noop") return;
          return { login: { token: state.phone + "-tk" }, close: true };
        }
    """.trimIndent()

    @Test
    fun renderByState() {
        val render = "$sourceJs\nloginUi(JSON.parse(String(__loginState)))"
        val first = LoginUiV2.parseRender(eval(render, "__loginState" to "{}"))
        assertEquals("phone", first!![0].key)
        val second = LoginUiV2.parseRender(
            eval(render, "__loginState" to """{"step":"code"}""")
        )
        assertEquals("code", second!![0].key)
        assertEquals(60, second[1].countdown)
    }

    @Test
    fun actionTemplateStringState() {
        // 模板字符串插值产生 ConsString,须经引擎 JSON.stringify 归一化不碎
        val dispatch = "$sourceJs\n" +
            "loginAction(String(__loginAction), JSON.parse(String(__loginState)), JSON.parse(String(__loginForm)))"
        val r = LoginUiV2.parseActionResult(
            eval(
                dispatch,
                "__loginAction" to "sendCode",
                "__loginState" to "{}",
                "__loginForm" to """{"phone":"13800000000"}""",
            )
        )
        assertTrue(r.stateJson!!.contains("+8613800000000"))
        assertNull(r.error)
    }

    @Test
    fun actionErrorAndUndefined() {
        val dispatch = "$sourceJs\n" +
            "loginAction(String(__loginAction), JSON.parse(String(__loginState)), JSON.parse(String(__loginForm)))"
        val err = LoginUiV2.parseActionResult(
            eval(dispatch, "__loginAction" to "sendCode", "__loginState" to "{}", "__loginForm" to "{}")
        )
        assertEquals("手机号必填", err.error!!["phone"])
        val neutral = LoginUiV2.parseActionResult(
            eval(dispatch, "__loginAction" to "noop", "__loginState" to "{}", "__loginForm" to "{}")
        )
        assertNull(neutral.stateJson)
        assertFalse(neutral.close)
    }
}
