package io.legado.app.lib.theme

import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent

/**
 * 用单个种子色生成 Material 3 调和配色（Content 方案）。
 *
 * Content 方案保证 primary == 用户选的颜色（种子即主色），
 * 同时派生出和谐的 secondary / tertiary / surface 等辅助色。
 *
 * 注意：MaterialDynamicColors 的取色 API 在 material 1.13.0 中为 public。
 */
@Suppress("RestrictedApi")
object M3ColorScheme {

    private val dynamicColors = MaterialDynamicColors()

    /** 从种子色生成一套 M3 颜色，主色=种子色 */
    class Scheme internal constructor(seedArgb: Int, isDark: Boolean) {
        private val scheme = SchemeContent(Hct.fromInt(seedArgb), isDark, 0.0)

        val primary: Int get() = dynamicColors.primary().getArgb(scheme)
        val onPrimary: Int get() = dynamicColors.onPrimary().getArgb(scheme)
        val secondary: Int get() = dynamicColors.secondary().getArgb(scheme)
        val tertiary: Int get() = dynamicColors.tertiary().getArgb(scheme)
        val surface: Int get() = dynamicColors.surface().getArgb(scheme)
        val surfaceVariant: Int get() = dynamicColors.surfaceVariant().getArgb(scheme)
        val onSurface: Int get() = dynamicColors.onSurface().getArgb(scheme)
        val background: Int get() = dynamicColors.background().getArgb(scheme)
        val error: Int get() = dynamicColors.error().getArgb(scheme)
    }

    fun generate(seedArgb: Int, isDark: Boolean): Scheme = Scheme(seedArgb, isDark)
}
