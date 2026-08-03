package io.legado.app.ui.book.read.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiConfigLayoutTest {

    private fun read(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun `the aloud preference screen carries the switch and the ai entry`() {
        val xml = read("res/xml/pref_config_aloud.xml")
        assertTrue("缺多角色开关", xml.contains("android:key=\"multiRoleReadAloud\""))
        assertTrue("缺 AI 服务入口", xml.contains("android:key=\"aiService\""))
    }

    @Test
    fun `the ai dialog collects base url key model and prompt`() {
        val xml = read("res/layout/dialog_ai_config.xml")
        listOf("til_ai_base_url", "til_ai_api_key", "til_ai_model", "til_ai_prompt")
            .forEach { assertTrue("缺 $it", xml.contains("android:id=\"@+id/$it\"")) }
        assertTrue("缺测试连接按钮", xml.contains("android:id=\"@+id/btn_test\""))
    }

    @Test
    fun `the scroll area keeps content clear of the rounded card corners`() {
        val xml = read("res/layout/dialog_ai_config.xml")
        assertTrue("圆角会裁字", xml.contains("android:clipToPadding=\"false\""))
        assertTrue("缺留边", xml.contains("@dimen/space_l"))
    }
}
