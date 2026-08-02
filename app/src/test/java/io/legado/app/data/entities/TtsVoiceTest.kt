package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceTest {

    @Test
    fun `blank voices json means the engine has a single voice`() {
        assertTrue(TtsVoice.parseList(null).isEmpty())
        assertTrue(TtsVoice.parseList("").isEmpty())
        assertTrue(TtsVoice.parseList("   ").isEmpty())
    }

    @Test
    fun `malformed json yields an empty list instead of throwing`() {
        assertTrue(TtsVoice.parseList("{not json").isEmpty())
        assertTrue(TtsVoice.parseList("""{"id":"x"}""").isEmpty())
    }

    @Test
    fun `entries without id are dropped`() {
        val list = TtsVoice.parseList("""[{"id":"a","name":"甲"},{"name":"乙"}]""")
        assertEquals(1, list.size)
        assertEquals("a", list[0].id)
    }

    @Test
    fun `unknown gender and age fall back to unknown`() {
        val list = TtsVoice.parseList("""[{"id":"a","gender":"robot","age":"ancient"}]""")
        assertEquals(TtsVoice.GENDER_UNKNOWN, list[0].gender)
        assertEquals(TtsVoice.AGE_UNKNOWN, list[0].age)
    }

    @Test
    fun `absent name falls back to id`() {
        val list = TtsVoice.parseList("""[{"id":"zh-CN-XiaoxiaoNeural"}]""")
        assertEquals("zh-CN-XiaoxiaoNeural", list[0].name)
    }

    @Test
    fun `well formed entry keeps its values`() {
        val list = TtsVoice.parseList(
            """[{"id":"zh-CN-XiaoxiaoNeural","name":"晓晓","gender":"female","age":"young"}]"""
        )
        assertEquals(TtsVoice("zh-CN-XiaoxiaoNeural", "晓晓", "female", "young"), list[0])
    }
}
