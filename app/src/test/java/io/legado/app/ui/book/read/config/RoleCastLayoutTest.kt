package io.legado.app.ui.book.read.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoleCastLayoutTest {

    private fun readLayout(name: String): String {
        val candidates = listOf(
            File("src/main/res/layout/$name"),
            File("app/src/main/res/layout/$name")
        )
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `the aloud panel has an entry next to the engine picker`() {
        val xml = readLayout("dialog_read_aloud.xml")
        assertTrue("朗读面板缺角色音色入口", xml.contains("android:id=\"@+id/ll_role_cast\""))
    }

    @Test
    fun `the role list rows carry name subtitle voice and preview`() {
        val xml = readLayout("item_role_cast.xml")
        listOf("tv_role_name", "tv_role_desc", "tv_role_voice", "iv_preview")
            .forEach { assertTrue("缺 $it", xml.contains("android:id=\"@+id/$it\"")) }
    }

    @Test
    fun `the dialog hosts a list and a reset action`() {
        val xml = readLayout("dialog_role_cast.xml")
        assertTrue("缺列表", xml.contains("android:id=\"@+id/recycler_view\""))
        assertTrue("缺重置入口", xml.contains("android:id=\"@+id/btn_reset\""))
    }
}
