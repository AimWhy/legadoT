package io.legado.app.model.login

import io.legado.app.data.entities.rule.RowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiV2Test {

    @Test
    fun v2Detection() {
        assertTrue(LoginUiV2.isV2("""{"version": 2}"""))
        assertTrue(LoginUiV2.isV2("""  {"version":2}  """))
        assertTrue(LoginUiV2.isV2(LoginUiV2.MARKER))
        assertFalse(LoginUiV2.isV2(null))
        assertFalse(LoginUiV2.isV2(""))
        assertFalse(LoginUiV2.isV2("""[{"name":"账号","type":"text"}]"""))
        assertFalse(LoginUiV2.isV2("""{"version": 1}"""))
        assertFalse(LoginUiV2.isV2("<js>buildUi()</js>"))
    }

    @Test
    fun parseRenderRows() {
        val rows = LoginUiV2.parseRender(
            """{"rows":[
                {"key":"phone","name":"手机号","type":"text","hint":"11位","value":"138"},
                {"name":"说明","type":"label"},
                {"key":"line","name":"线路","type":"select","options":["电信","联通"],"value":"电信"},
                {"key":"remember","name":"记住登录","type":"toggle","value":"true"},
                {"name":"发码","type":"button","action":"sendCode","countdown":60}
            ]}"""
        )
        assertEquals(5, rows!!.size)
        assertEquals("phone", rows[0].key)
        assertEquals("11位", rows[0].hint)
        assertEquals("138", rows[0].value)
        assertEquals("label", rows[1].type)
        assertEquals(listOf("电信", "联通"), rows[2].options)
        assertEquals("电信", rows[2].value)
        assertEquals(RowUi.Type.toggle, rows[3].type)
        assertEquals("true", rows[3].value)
        assertEquals(60, rows[4].countdown)
    }

    @Test
    fun parseRenderMalformed() {
        assertNull(LoginUiV2.parseRender(null))
        assertNull(LoginUiV2.parseRender(""))
        assertNull(LoginUiV2.parseRender("""{"noRows":true}"""))
        assertNull(LoginUiV2.parseRender("""[{"name":"x"}]"""))
        assertNull(LoginUiV2.parseRender("not json"))
    }

    @Test
    fun parseActionResultFull() {
        val r = LoginUiV2.parseActionResult(
            """{"state":{"step":"code"},"error":{"phone":"格式不对"},
               "login":{"token":"t1"},"close":true,"typo":1}"""
        )
        assertEquals("""{"step":"code"}""", r.stateJson)
        assertEquals("格式不对", r.error!!["phone"])
        assertEquals("""{"token":"t1"}""", r.loginJson)
        assertTrue(r.close)
        assertEquals(listOf("typo"), r.unknownKeys)
    }

    @Test
    fun parseActionResultNeutral() {
        listOf(null, "", "not json", "[1,2]").forEach {
            val r = LoginUiV2.parseActionResult(it)
            assertNull(r.stateJson)
            assertNull(r.error)
            assertNull(r.loginJson)
            assertFalse(r.close)
        }
    }

    @Test
    fun fieldValuePrecedence() {
        assertEquals("render", LoginUiV2.resolveFieldValue("render", "typed", "stored"))
        assertEquals("", LoginUiV2.resolveFieldValue("", "typed", "stored")) // "" 是合法强制清空
        assertEquals("typed", LoginUiV2.resolveFieldValue(null, "typed", "stored"))
        assertEquals("stored", LoginUiV2.resolveFieldValue(null, null, "stored"))
        assertNull(LoginUiV2.resolveFieldValue(null, null, null))
    }

    @Test
    fun toggleValuePrecedenceAndDefault() {
        assertEquals("true", LoginUiV2.resolveToggleValue("true", "false", "false"))
        assertEquals("false", LoginUiV2.resolveToggleValue("false", "true", "true"))
        assertEquals("true", LoginUiV2.resolveToggleValue(null, "true", "false"))
        assertEquals("true", LoginUiV2.resolveToggleValue(null, null, "true"))
        assertEquals("false", LoginUiV2.resolveToggleValue(null, null, null))
    }
}
