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

    @Test
    fun `explicit null name falls back to id`() {
        val list = TtsVoice.parseList("""[{"id":"a","name":null}]""")
        assertEquals(1, list.size)
        assertEquals("a", list[0].name)
    }

    @Test
    fun `explicit null id is dropped`() {
        assertTrue(TtsVoice.parseList("""[{"id":null}]""").isEmpty())
        assertTrue(TtsVoice.parseList("""[{"id":null,"name":"乙"}]""").isEmpty())
    }

    @Test
    fun `explicit null gender and age fall back to unknown`() {
        val list = TtsVoice.parseList("""[{"id":"a","gender":null,"age":null}]""")
        assertEquals(TtsVoice.GENDER_UNKNOWN, list[0].gender)
        assertEquals(TtsVoice.AGE_UNKNOWN, list[0].age)
    }

    @Test
    fun `gender and age accept mixed case and surrounding blanks`() {
        val list = TtsVoice.parseList(
            """[{"id":"a","gender":"Female","age":"Young"},{"id":"b","gender":" female ","age":"  MIDDLE  "}]"""
        )
        assertEquals(TtsVoice.GENDER_FEMALE, list[0].gender)
        assertEquals("young", list[0].age)
        assertEquals(TtsVoice.GENDER_FEMALE, list[1].gender)
        assertEquals("middle", list[1].age)
    }

    @Test
    fun `unrecognized value stays unknown after normalization`() {
        val list = TtsVoice.parseList("""[{"id":"a","gender":"ROBOT","age":" ancient "}]""")
        assertEquals(TtsVoice.GENDER_UNKNOWN, list[0].gender)
        assertEquals(TtsVoice.AGE_UNKNOWN, list[0].age)
    }

    @Test
    fun `whitespace only id is dropped`() {
        assertTrue(TtsVoice.parseList("""[{"id":"   ","name":"甲"}]""").isEmpty())
    }

    @Test
    fun `well formed array whose entries all lack id yields an empty list`() {
        assertTrue(TtsVoice.parseList("""[{"name":"晓晓"},{"name":"云希"}]""").isEmpty())
    }

    @Test
    fun `dropping every entry does not throw`() {
        assertTrue(TtsVoice.parseList("""[{"id":"  "},{"id":null},{"gender":"female"}]""").isEmpty())
    }

    @Test
    fun `id and name are trimmed`() {
        val list = TtsVoice.parseList("""[{"id":" a ","name":" 甲 "}]""")
        assertEquals(1, list.size)
        assertEquals("a", list[0].id)
        assertEquals("甲", list[0].name)
    }

    @Test
    fun `name falls back to the trimmed id`() {
        val list = TtsVoice.parseList("""[{"id":" a "}]""")
        assertEquals("a", list[0].name)
    }

    @Test
    fun `blank name falls back to the trimmed id`() {
        val list = TtsVoice.parseList("""[{"id":" a ","name":"   "}]""")
        assertEquals("a", list[0].id)
        assertEquals("a", list[0].name)
    }

    @Test
    fun `strict validation rejects duplicate ids and invalid enums`() {
        assertTrue(TtsVoice.validateList("""[{"id":"a"},{"id":"a"}]""").isFailure)
        assertTrue(TtsVoice.validateList("""[{"id":"a","gender":"robot"}]""").isFailure)
        assertTrue(TtsVoice.validateList("""[{"id":"a","age":"ancient"}]""").isFailure)
    }

    @Test
    fun `strict validation accepts an empty list and normalized values`() {
        assertTrue(TtsVoice.validateList(null).isSuccess)
        assertEquals(
            listOf(TtsVoice("a", "a", "female", "young")),
            TtsVoice.validateList("""[{"id":"a","gender":"Female","age":"Young"}]""").getOrThrow()
        )
    }
}
