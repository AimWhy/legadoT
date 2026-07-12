package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadAloudBackToSpeechActionTest {

    @Test
    fun `floating capsule exposes back to speaking position control`() {
        // 回位控件已从朗读弹窗迁至底部悬浮胶囊(脱离跟随时浮现);弹窗侧按钮已退役。
        val xml = readProjectFile("src/main/res/layout/view_read_aloud_float_bar.xml")

        assertTrue(xml.contains("@+id/ll_back_to_speech"))
        assertTrue(xml.contains("@string/back_to_speaking_position"))
    }

    @Test
    fun `read aloud dialog no longer carries back to speech button`() {
        // 弹窗回位钮退役(功能归胶囊),避免双入口。
        val xml = readProjectFile("src/main/res/layout/dialog_read_aloud.xml")
        assertFalse(xml.contains("@+id/iv_back_to_speech"))
    }

    @Test
    fun `back to speaking wiring restores follow and requests jump`() {
        val dialogKt = readProjectFile("src/main/java/io/legado/app/ui/book/read/config/ReadAloudDialog.kt")
        val activityKt = readProjectFile("src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt")

        // 接口契约保留(胶囊经 Activity 实现调用)
        assertTrue(dialogKt.contains("fun backToSpeakingPosition()"))
        assertTrue(activityKt.contains("override fun backToSpeakingPosition()"))
        assertTrue(activityKt.contains("ReadAloud.restoreReadAloudFollow()"))
        // 胶囊回位段接线
        assertTrue(activityKt.contains("llBackToSpeech.setOnClickListener"))
    }

    @Test
    fun `cross chapter jump opens the speaking chapter at the spoken position`() {
        val activityKt = readProjectFile("src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt")

        // Cross-chapter must be precise: open the speaking chapter AT the spoken char position,
        // not just the chapter start.
        assertTrue(activityKt.contains("ReadBook.openChapter(speakingChapterIndex,"))
    }

    @Test
    fun `back to speaking position strings are localized`() {
        val defaultStrings = readProjectFile("src/main/res/values/strings.xml")
        val zhStrings = readProjectFile("src/main/res/values-zh/strings.xml")

        assertTrue(defaultStrings.contains("<string name=\"back_to_speaking_position\">Back to speaking position</string>"))
        assertTrue(zhStrings.contains("<string name=\"back_to_speaking_position\">回到朗读位置</string>"))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(
            File(pathInApp),
            File("app/$pathInApp")
        )
        return candidates.first { it.isFile }.readText()
    }
}
