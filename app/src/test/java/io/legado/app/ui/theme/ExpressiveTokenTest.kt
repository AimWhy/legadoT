package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExpressiveTokenTest {

    @Test
    fun `radius scale is expressive recalibrated - containers grow small stays`() {
        val dimens = readProjectFile("src/main/res/values/dimens.xml")
        assertContains(dimens, "<dimen name=\"radius_xs\">4dp</dimen>")
        assertContains(dimens, "<dimen name=\"radius_s\">8dp</dimen>")
        assertContains(dimens, "<dimen name=\"radius_m\">12dp</dimen>")
        assertContains(dimens, "<dimen name=\"radius_l\">20dp</dimen>")
        assertContains(dimens, "<dimen name=\"radius_xl\">32dp</dimen>")
        assertContains(dimens, "<dimen name=\"radius_full\">999dp</dimen>")
    }

    @Test
    fun `display tier and gap tokens exist`() {
        val dimens = readProjectFile("src/main/res/values/dimens.xml")
        assertContains(dimens, "<dimen name=\"text_display\">32sp</dimen>")
        assertContains(dimens, "<dimen name=\"text_caption_l\">13sp</dimen>")
        assertContains(dimens, "<dimen name=\"text_body_l\">15sp</dimen>")
        assertContains(dimens, "<dimen name=\"text_subtitle_l\">17sp</dimen>")
    }

    @Test
    fun `headline is emphasized and display style hooked`() {
        val styles = readProjectFile("src/main/res/values/styles.xml")
        assertContains(styles, "name=\"TextAppearance.App.Display\"")
        assertContains(styles, "<item name=\"textAppearanceDisplaySmall\">@style/TextAppearance.App.Display</item>")
        val headlineStart = styles.indexOf("name=\"TextAppearance.App.Headline\"")
        assertTrue("TextAppearance.App.Headline block not found", headlineStart >= 0)
        val headlineEnd = styles.indexOf("</style>", headlineStart)
        assertTrue("TextAppearance.App.Headline block not closed", headlineEnd >= 0)
        val headlineBlock = styles.substring(headlineStart, headlineEnd)
        assertContains(headlineBlock, "<item name=\"android:fontFamily\">sans-serif</item>")
        assertContains(headlineBlock, "<item name=\"android:textStyle\">bold</item>")
    }

    @Test
    fun `textInputStyle is hooked and source edit chips use app token`() {
        val styles = readProjectFile("src/main/res/values/styles.xml")
        assertContains(styles, "<item name=\"textInputStyle\">@style/Widget.App.TextInputLayout</item>")
        val layout = readProjectFile("src/main/res/layout/activity_book_source_edit.xml")
        assertTrue("chips should use Widget.App.Chip", !layout.contains("Widget.Material3.Chip.Filter"))
    }

    private fun readProjectFile(path: String): String = File(path).readText()

    private fun assertContains(haystack: String, needle: String) {
        assertTrue("missing: $needle", haystack.contains(needle))
    }
}
