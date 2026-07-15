package io.legado.app.ui.widget.code

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSafetyTest {

    @Test
    fun `combining mark bomb is detected`() {
        val bomb = "a" + "́".repeat(12) + "b"
        assertTrue(EditSafety.isCombiningHeavy(bomb))
    }

    @Test
    fun `many scattered combining marks are detected`() {
        val text = buildString { repeat(70) { append('e').append('́') } }
        assertTrue(EditSafety.isCombiningHeavy(text))
    }

    @Test
    fun `normal rule text passes`() {
        assertFalse(EditSafety.isCombiningHeavy(""))
        assertFalse(EditSafety.isCombiningHeavy("@css:.book-item@href##\\d+##<js>result</js>"))
        assertFalse(EditSafety.isCombiningHeavy("var a = 1; // 中文注释"))
    }

    @Test
    fun `emoji variation selectors are not treated as combining bomb`() {
        assertFalse(EditSafety.isCombiningHeavy("-☯️☯️☯️☯️☯️☯️☯️-"))
    }
}
