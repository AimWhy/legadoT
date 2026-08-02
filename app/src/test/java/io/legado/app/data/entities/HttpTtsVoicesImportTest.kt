package io.legado.app.data.entities

import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 导入侧 voices 的两种书写形态。JSON 数组是手写引擎配置的自然形态,
 * 转义 JSON 字符串是本 App 导出的形态, 二者须导入出等价的音色清单
 */
class HttpTtsVoicesImportTest {

    private val expected = listOf(
        TtsVoice("zh-CN-XiaoxiaoNeural", "晓晓", TtsVoice.GENDER_FEMALE, "young"),
        TtsVoice("zh-CN-YunxiNeural", "云希", TtsVoice.GENDER_MALE, "middle")
    )

    private val voicesJson =
        """[{"id":"zh-CN-XiaoxiaoNeural","name":"晓晓","gender":"female","age":"young"},""" +
            """{"id":"zh-CN-YunxiNeural","name":"云希","gender":"male","age":"middle"}]"""

    private fun engineJson(voicesField: String?): String {
        val voices = voicesField?.let { ""","voices":$it""" }.orEmpty()
        return """{"id":1,"name":"某引擎","url":"http://a.com/tts"$voices}"""
    }

    private fun importVoices(voicesField: String?): String? =
        HttpTTS.fromJson(engineJson(voicesField)).getOrThrow().voices

    @Test
    fun `voices authored as a real json array is kept`() {
        val stored = importVoices(voicesJson)
        assertEquals(expected, TtsVoice.parseList(stored))
    }

    @Test
    fun `voices authored as an escaped json string is kept`() {
        val stored = importVoices(GSON.toJson(voicesJson))
        assertEquals(expected, TtsVoice.parseList(stored))
    }

    @Test
    fun `both authoring forms import to the same voice list`() {
        assertEquals(
            TtsVoice.parseList(importVoices(voicesJson)),
            TtsVoice.parseList(importVoices(GSON.toJson(voicesJson)))
        )
    }

    @Test
    fun `escaped json string is stored verbatim`() {
        assertEquals(voicesJson, importVoices(GSON.toJson(voicesJson)))
    }

    @Test
    fun `absent voices imports as null`() {
        assertNull(importVoices(null))
    }

    @Test
    fun `explicit null voices imports as null`() {
        assertNull(importVoices("null"))
    }

    @Test
    fun `single voice entry survives the array form`() {
        val stored = importVoices("""[{"id":"zh-CN-XiaoxiaoNeural"}]""")
        assertEquals(
            listOf(TtsVoice("zh-CN-XiaoxiaoNeural", "zh-CN-XiaoxiaoNeural")),
            TtsVoice.parseList(stored)
        )
    }

    @Test
    fun `other fields still import alongside an array shaped voices`() {
        val httpTTS = HttpTTS.fromJson(engineJson(voicesJson)).getOrThrow()
        assertEquals(1L, httpTTS.id)
        assertEquals("某引擎", httpTTS.name)
        assertEquals("http://a.com/tts", httpTTS.url)
    }

    @Test
    fun `export then import preserves voices`() {
        val httpTTS = HttpTTS(id = 7, name = "某引擎", url = "http://a.com/tts", voices = voicesJson)
        val back = HttpTTS.fromJson(GSON.toJson(httpTTS)).getOrThrow()
        assertEquals(voicesJson, back.voices)
        assertEquals(expected, TtsVoice.parseList(back.voices))
    }

    @Test
    fun `export then import of an engine without voices stays null`() {
        val httpTTS = HttpTTS(id = 7, name = "某引擎", url = "http://a.com/tts")
        assertNull(HttpTTS.fromJson(GSON.toJson(httpTTS)).getOrThrow().voices)
    }
}
