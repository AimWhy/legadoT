package io.legado.app.ui.book.read.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HttpTtsVoicesEditTest {

    private fun read(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `the engine editor exposes a voices field`() {
        val xml = read("res/layout/dialog_http_tts_edit.xml")
        assertTrue("缺音色清单输入", xml.contains("android:id=\"@+id/tv_voices\""))
    }

    @Test
    fun `the editor reads and writes the voices column`() {
        val src = read("java/io/legado/app/ui/book/read/config/HttpTtsEditDialog.kt")
        assertTrue("未回填音色清单", src.contains("tvVoices.setText(httpTTS.voices"))
        assertTrue("未保存音色清单", src.contains("voices ="))
    }
}
