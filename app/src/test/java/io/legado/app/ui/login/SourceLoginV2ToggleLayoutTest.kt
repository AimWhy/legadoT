package io.legado.app.ui.login

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceLoginV2ToggleLayoutTest {

    @Test
    fun `v2 toggle row uses a Material switch`() {
        val xml = readProjectFile("src/main/res/layout/item_login_toggle.xml")

        assertTrue(xml.contains("com.google.android.material.materialswitch.MaterialSwitch"))
        assertTrue(xml.contains("android:id=\"@+id/toggle\""))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
