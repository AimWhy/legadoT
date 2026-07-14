package io.legado.app.ui.book.read.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TipConfigDialogTemplateWiringTest {

    @Test
    fun `template editor layout is scrollable and exposes edit and placeholder controls`() {
        val layout = readProjectFile("src/main/res/layout/dialog_reader_info_template.xml")

        assertTrue(layout.contains("androidx.core.widget.NestedScrollView"))
        assertTrue(layout.contains("io.legado.app.lib.theme.view.ThemeEditText"))
        assertTrue(layout.contains("android:id=\"@+id/edit_template\""))
        assertTrue(layout.contains("android:hint=\"@string/reader_info_template_hint\""))
        assertTrue(layout.contains("android:maxLines=\"1\""))
        assertTrue(layout.contains("android:singleLine=\"true\""))
        assertTrue(layout.contains("com.google.android.material.chip.ChipGroup"))
        assertTrue(layout.contains("android:id=\"@+id/chip_placeholders\""))
    }

    @Test
    fun `all reader info rows edit and summarize their matching template fields`() {
        val source = readProjectFile(
            "src/main/java/io/legado/app/ui/book/read/config/TipConfigDialog.kt"
        )
        val pairs = mapOf(
            "llHeaderLeft" to "tipHeaderLeftTemplate" to "tipHeaderLeft",
            "llHeaderMiddle" to "tipHeaderMiddleTemplate" to "tipHeaderMiddle",
            "llHeaderRight" to "tipHeaderRightTemplate" to "tipHeaderRight",
            "llFooterLeft" to "tipFooterLeftTemplate" to "tipFooterLeft",
            "llFooterMiddle" to "tipFooterMiddleTemplate" to "tipFooterMiddle",
            "llFooterRight" to "tipFooterRightTemplate" to "tipFooterRight",
        )

        pairs.forEach { (row, fields) ->
            val (template, legacy) = fields
            assertTrue(source.contains("$row.constrainValueWidth()"))
            assertTrue(source.contains("$row.value = effectiveTemplate($template, $legacy)"))
            assertTrue(source.contains("$row.setOnClickListener"))
            assertTrue(source.contains("current = effectiveTemplate($template, $legacy)"))
            assertTrue(source.contains("$template = it"))
        }
        assertEquals(6, Regex("\\.constrainValueWidth\\(\\)").findAll(source).count())
        assertFalse(source.contains("tipTemplatePreview"))
        assertFalse(source.contains("TIP_TEMPLATE_PREVIEW_LIMIT"))
    }

    @Test
    fun `value width constraint is opt in capped and recalculated on size changes`() {
        val source = readProjectFile(
            "src/main/java/io/legado/app/ui/widget/LabeledValueRow.kt"
        )
        val compactSource = source.replace(Regex("\\s+"), " ")

        assertTrue(source.contains("fun constrainValueWidth(maxRowFraction: Float = 0.5f)"))
        assertTrue(source.contains("maxRowFraction.coerceAtMost(0.5f)"))
        assertTrue(source.contains("binding.tvValue.isSingleLine = true"))
        assertTrue(source.contains("binding.tvValue.ellipsize = TextUtils.TruncateAt.END"))
        assertTrue(source.contains("override fun onLayout"))
        assertTrue(source.contains("updateValueMaxWidth(right - left)"))
        assertTrue(
            compactSource.contains(
                "val targetWidth = (rowWidth * fraction).toInt() " +
                    "if (binding.tvValue.maxWidth != targetWidth) { " +
                    "binding.tvValue.maxWidth = targetWidth }"
            )
        )
        assertFalse(source.contains("addOnLayoutChangeListener"))
    }

    @Test
    fun `editor normalizes selection and uses editable text for replace and save`() {
        val source = readProjectFile(
            "src/main/java/io/legado/app/ui/book/read/config/TipConfigDialog.kt"
        )
        val compactSource = source.replace(Regex("\\s+"), " ").replace(" .", ".")

        assertTrue(source.contains("ReaderInfoTemplate.placeholders.forEach"))
        assertTrue(source.contains("val editable = edit.editableText"))
        assertTrue(
            compactSource.contains(
                "minOf(edit.selectionStart, edit.selectionEnd).coerceIn(0, editable.length)"
            )
        )
        assertTrue(
            compactSource.contains(
                "maxOf(edit.selectionStart, edit.selectionEnd).coerceIn(0, editable.length)"
            )
        )
        assertTrue(source.contains("editable.replace(start, end, placeholder)"))
        assertTrue(source.contains("save(dialogBinding.editTemplate.editableText.toString())"))
        assertFalse(source.contains("edit.text?.replace"))
        assertFalse(source.contains("editTemplate.text.toString()"))
        assertTrue(source.contains("postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))"))
        assertFalse(source.contains("clearRepeat"))
    }

    private infix fun Pair<String, String>.to(legacy: String) =
        first to (second to legacy)

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(File(pathInApp), File("app/$pathInApp"))
        return candidates.first { it.isFile }.readText()
    }
}
