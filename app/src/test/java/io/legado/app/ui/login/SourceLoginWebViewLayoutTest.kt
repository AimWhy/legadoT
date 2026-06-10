package io.legado.app.ui.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceLoginWebViewLayoutTest {

    @Test
    fun `web view login fragment paints runtime theme background inside transparent login activity`() {
        val xml = readProjectFile("src/main/res/layout/fragment_web_view_login.xml")
        val kt = readProjectFile("src/main/java/io/legado/app/ui/login/WebViewLoginFragment.kt")

        assertFalse(
            "WebView login must not hard-code @color/background because user-selected theme colors live in ThemeStore",
            xml.contains("android:background=\"@color/background\"")
        )
        assertTrue(
            "WebView login must paint an opaque runtime theme background so SourceLoginActivity's transparent theme cannot reveal the previous page toolbar underneath",
            kt.contains("binding.root.setBackgroundColor(requireContext().backgroundColor)")
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
