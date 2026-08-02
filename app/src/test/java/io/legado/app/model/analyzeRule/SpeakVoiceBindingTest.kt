package io.legado.app.model.analyzeRule

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeakVoiceBindingTest {

    private val source: String by lazy {
        val candidates = listOf(
            File("src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt"),
            File("app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt")
        )
        candidates.first { it.isFile }.readText()
    }

    @Test
    fun `speakVoice is a constructor parameter next to speakSpeed`() {
        assertTrue(
            "缺 speakVoice 构造参数",
            source.contains("private val speakVoice: String? = null,")
        )
    }

    @Test
    fun `speakVoice is exposed to js alongside speakText and speakSpeed`() {
        assertTrue(
            "speakVoice 未绑定到 JS 作用域",
            source.contains("""bindings["speakVoice"] = speakVoice""")
        )
    }
}
