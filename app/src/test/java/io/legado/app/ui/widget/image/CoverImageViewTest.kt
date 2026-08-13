package io.legado.app.ui.widget.image

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoverImageViewTest {

    @Test
    fun `transparent cover pixels use the current page background`() {
        val source = File(
            "src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt"
        ).readText()

        assertTrue(source.contains("import io.legado.app.lib.theme.backgroundColor"))
        assertTrue(source.contains("setBackgroundColor(context.backgroundColor)"))
    }
}
