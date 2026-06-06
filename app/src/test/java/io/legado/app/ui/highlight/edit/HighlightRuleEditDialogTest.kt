package io.legado.app.ui.highlight.edit

import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.data.entities.BookHighlight
import io.legado.app.help.HighlightColors
import io.legado.app.ui.book.read.HighlightActionMenu.Companion.HL_FILL
import io.legado.app.ui.book.read.HighlightActionMenu.Companion.HL_TEXT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HighlightRuleEditDialogTest {

    private class Listener : ColorPickerDialogListener {
        override fun onColorSelected(dialogId: Int, color: Int) = Unit
        override fun onDialogDismissed(dialogId: Int) = Unit
    }

    @Test
    fun `binds color picker callbacks to supplied listener`() {
        val listener = Listener()
        val dialog = ColorPickerDialog()

        val bound = HighlightRuleEditDialog.bindColorPickerListener(dialog, listener)

        assertSame(dialog, bound)
        assertSame(listener, dialog.boundColorPickerListener())
    }

    @Test
    fun `color picker config uses fallback seed and requested presets`() {
        val config = HighlightRuleEditDialog.colorPickerConfig(
            dialogId = HL_TEXT,
            initial = 0,
            withAlpha = false
        )

        assertEquals(HL_TEXT, config.dialogId)
        assertEquals(HighlightColors.bg.first(), config.color)
        assertEquals(false, config.withAlpha)
        assertArrayEquals(HighlightColors.text, config.presets)
    }

    @Test
    fun `color picker config keeps non-zero initial color`() {
        val color = 0xFF123456.toInt()

        val config = HighlightRuleEditDialog.colorPickerConfig(
            dialogId = HL_FILL,
            initial = color,
            withAlpha = true
        )

        assertEquals(color, config.color)
        assertEquals(true, config.withAlpha)
        assertArrayEquals(HighlightColors.bg, config.presets)
    }

    @Test
    fun `batch keeps source highlight when no source time`() {
        val list = listOf(BookHighlight(time = 1L), BookHighlight(time = 2L))

        assertNull(HighlightRuleEditDialog.highlightToRemove(list, 0L))
    }

    @Test
    fun `batch removes the originating manual highlight`() {
        val target = BookHighlight(time = 2L)
        val list = listOf(BookHighlight(time = 1L), target, BookHighlight(time = 3L))

        assertSame(target, HighlightRuleEditDialog.highlightToRemove(list, 2L))
    }

    @Test
    fun `batch keeps highlights when source time has no match`() {
        val list = listOf(BookHighlight(time = 1L))

        assertNull(HighlightRuleEditDialog.highlightToRemove(list, 999L))
    }

    private fun ColorPickerDialog.boundColorPickerListener(): ColorPickerDialogListener? {
        val field = ColorPickerDialog::class.java.getDeclaredField("colorPickerDialogListener")
        field.isAccessible = true
        return field.get(this) as? ColorPickerDialogListener
    }
}
