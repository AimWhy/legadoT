package io.legado.app.ui.dialog

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DialogInputSpacingLayoutTest {

    @Test
    fun `rounded card dialog inputs are inset from card edges`() {
        assertContentPadding("dialog_toc_regex_edit.xml")
        assertContentPadding("dialog_dict_rule_edit.xml")
        assertContentPadding("dialog_http_tts_edit.xml")

        assertContains(
            "dialog_webdav_server.xml",
            "android:layout_marginHorizontal=\"@dimen/space_l\""
        )
        assertContains(
            "dialog_variable.xml",
            "android:layout_marginHorizontal=\"@dimen/space_l\""
        )
        assertContains(
            "dialog_verification_code_view.xml",
            "android:layout_marginHorizontal=\"@dimen/space_l\""
        )
    }

    @Test
    fun `stacked dialog text fields have vertical breathing room`() {
        assertTopMarginCount("dialog_toc_regex_edit.xml", 2)
        assertTopMarginCount("dialog_dict_rule_edit.xml", 2)
        assertTopMarginCount("dialog_http_tts_edit.xml", 8)
        assertTopMarginCount("dialog_url_option_edit.xml", 7)
        assertTopMarginCount("dialog_rule_sub_edit.xml", 1)
        assertElementContains(
            "dialog_select_section_export.xml",
            "android:id=\"@+id/ly_et_input_scope\"",
            "android:layout_marginTop=\"12dp\""
        )
    }

    private fun assertContentPadding(layoutName: String) {
        assertContains(layoutName, "android:paddingHorizontal=\"@dimen/space_l\"")
    }

    private fun assertTopMarginCount(layoutName: String, minCount: Int) {
        val xml = readLayoutXml(layoutName)
        val count = "android:layout_marginTop=\"@dimen/space_m\"".toRegex().findAll(xml).count()
        assertTrue("$layoutName should have at least $minCount spaced stacked fields, found $count", count >= minCount)
    }

    private fun assertContains(layoutName: String, expected: String) {
        val xml = readLayoutXml(layoutName)
        assertTrue("$layoutName should contain $expected", xml.contains(expected))
    }

    private fun assertElementContains(layoutName: String, anchor: String, expected: String) {
        val xml = readLayoutXml(layoutName)
        val start = xml.indexOf(anchor)
        assertTrue("$layoutName should contain $anchor", start >= 0)
        val end = xml.indexOf('>', start)
        assertTrue("$layoutName should close element containing $anchor", end > start)
        val element = xml.substring(start, end)
        assertTrue("$layoutName element $anchor should contain $expected", element.contains(expected))
    }

    private fun readLayoutXml(layoutName: String): String {
        val candidates = listOf(
            File("src/main/res/layout/$layoutName"),
            File("app/src/main/res/layout/$layoutName")
        )
        return candidates.first { it.isFile }.readText()
    }
}
