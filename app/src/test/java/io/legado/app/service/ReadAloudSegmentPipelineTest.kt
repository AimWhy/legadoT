package io.legado.app.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadAloudSegmentPipelineTest {

    private fun readSource(name: String): String {
        val candidates = listOf(
            File("src/main/java/io/legado/app/service/$name"),
            File("app/src/main/java/io/legado/app/service/$name")
        )
        return candidates.first { it.isFile }.readText()
    }

    private val base by lazy { readSource("BaseReadAloudService.kt") }
    private val http by lazy { readSource("HttpReadAloudService.kt") }

    @Test
    fun `the cursor carries a segment index alongside the paragraph index`() {
        assertTrue("缺片段游标", base.contains("internal var nowSegment: Int = 0"))
        assertTrue("缺脚本持有", base.contains("internal var speechScript: SpeechScript?"))
        assertTrue("缺脚本兜底取用", base.contains("fun currentScript(): SpeechScript"))
    }

    @Test
    fun `paragraph level navigation resets the segment cursor`() {
        // prevP / nextP / newReadAloud 三处都必须归零, 否则跳段后会从错误的片段起播
        assertTrue("片段游标归零点不足", Regex("nowSegment = 0").findAll(base).count() >= 3)
    }

    @Test
    fun `the narrator fallback is resolved off the playback callback path`() {
        // currentScript 跑在 ExoPlayer 回调线程上, 只读缓存; 取库放在下载协程的 IO 上下文里
        val currentScript = base.substringAfter("fun currentScript(): SpeechScript")
            .substringBefore("\n    }")
        assertTrue("currentScript 不得为 suspend", !base.contains("suspend fun currentScript()"))
        assertTrue("currentScript 内不得取库", !currentScript.contains("RoleCastManager"))
        assertTrue("缺旁白 casting 预解析", base.contains("suspend fun prepareSpeechScript()"))
        assertTrue("预解析未接入下载协程", http.contains("prepareSpeechScript()"))
    }

    @Test
    fun `updateNextPos advances within the paragraph before moving on`() {
        assertTrue("缺段内推进分支", http.contains("if (nowSegment < segs.lastIndex)"))
    }

    @Test
    fun `media ids are segment scoped while pause items stay two part`() {
        assertTrue("mediaId 未扩到片段", http.contains("\"\$sessionId:\$para:\$segIndex\""))
        assertTrue("停顿项 id 变了会打断既有判定", http.contains("\"\$sessionId:-1\""))
    }

    @Test
    fun `the cache key separates engine and voice`() {
        assertTrue(
            "换音色会命中旧缓存",
            http.contains("\${cast.ttsEngineId}-|-\${cast.voice}")
        )
    }

    @Test
    fun `an unspecified engine id falls back to the current engine`() {
        assertTrue("引擎 id 为 0 时须回退当前引擎", http.contains("engineId <= 0L -> null"))
        assertTrue("缺当前引擎兜底", http.contains("?: ReadAloud.httpTTS ?: throw"))
    }

    @Test
    fun `the speak request carries the voice`() {
        assertTrue("取流未传音色", http.contains("speakVoice = voice"))
    }
}
