package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N4 主题 UX 哨兵 */
class ThemeUxTest {
    @Test
    fun `seed applier writes pref keys and goes through applyDayNight`() {
        val src = File("src/main/java/io/legado/app/lib/theme/ThemeSeedApplier.kt").readText()
        assertTrue(src.contains("applyDayNight"))
        assertTrue("红线:不得绕过 applyTheme 直写 ThemeStore", !src.contains("ThemeStore.editTheme"))
        assertTrue(src.contains("fun applySeed"))
    }
}
