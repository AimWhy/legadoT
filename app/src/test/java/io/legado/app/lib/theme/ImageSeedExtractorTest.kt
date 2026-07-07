package io.legado.app.lib.theme

import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("RestrictedApi")
class ImageSeedExtractorTest {

    @Test
    fun `dominant red pixels yield red-family seed`() {
        val pixels = IntArray(4096) { if (it % 10 == 0) 0xFF2244CC.toInt() else 0xFFCC2222.toInt() }
        val seed = ImageSeedExtractor.extractSeed(pixels)
        assertNotNull(seed)
        val hue = Hct.fromInt(seed!!).hue
        assertTrue("期望红系色相,实得 $hue", hue < 60.0 || hue > 330.0)
    }

    @Test
    fun `empty input yields null`() {
        assertNull(ImageSeedExtractor.extractSeed(IntArray(0)))
    }
}
