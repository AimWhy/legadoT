package io.legado.app.ui.book.read

import io.legado.app.help.HighlightStyle
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HighlightStyleDialogTest {

    private class Host : HighlightStyleDialog.StyleHost {
        override fun currentHighlightStyle(): HighlightStyle = HighlightStyle()
        override fun onHighlightStyleChanged(style: HighlightStyle) = Unit
        override fun pickHighlightColor(dialogId: Int, initial: Int, withAlpha: Boolean) = Unit
    }

    @Test
    fun `resolves nested dialog parent as style host`() {
        val parentHost = Host()
        val activityHost = Host()

        val resolved = HighlightStyleDialog.resolveStyleHost(parentHost, activityHost)

        assertSame(parentHost, resolved)
    }

    @Test
    fun `falls back to activity as style host`() {
        val activityHost = Host()

        val resolved = HighlightStyleDialog.resolveStyleHost(null, activityHost)

        assertSame(activityHost, resolved)
    }

    @Test
    fun `returns null when neither parent nor activity is style host`() {
        val resolved = HighlightStyleDialog.resolveStyleHost(Any(), Any())

        assertNull(resolved)
    }
}
