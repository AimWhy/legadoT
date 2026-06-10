package io.legado.app.ui.login

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceLoginWebViewLayoutTest {

    @Test
    fun `web view login fragment uses opaque background inside transparent login activity`() {
        val xml = readProjectFile("src/main/res/layout/fragment_web_view_login.xml")

        assertTrue(
            "WebView login must paint an opaque app background so SourceLoginActivity's transparent theme cannot reveal the previous page toolbar underneath",
            xml.contains("android:background=\"@color/background\"")
        )
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(
            File(pathInApp),
            File("app/$pathInApp")
        )
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
