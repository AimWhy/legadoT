package io.legado.app.ui.widget.dialog

import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.utils.dpToPx
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TableTheme

/**
 * MD 弹窗排版主题:标题分级/分隔线/代码块/内联码/引用/链接/表格,
 * 施色走 AppColorScheme,暗色与换肤自动适配。
 */
object HelpMarkwonTheme {

    fun plugin(): MarkwonPlugin = object : AbstractMarkwonPlugin() {
        override fun configureTheme(builder: MarkwonTheme.Builder) {
            val scheme = AppColorScheme.current
            builder
                .headingTextSizeMultipliers(floatArrayOf(1.45f, 1.3f, 1.15f, 1.05f, 1f, 1f))
                .headingBreakColor(scheme.outlineVariant)
                .codeBlockBackgroundColor(scheme.surfaceContainerHigh)
                .codeBlockTextColor(scheme.onSurface)
                .codeBlockMargin(8.dpToPx())
                .codeBackgroundColor(scheme.surfaceContainerHighest)
                .codeTextColor(scheme.onSurface)
                .blockQuoteColor(scheme.primary)
                .blockQuoteWidth(3.dpToPx())
                .listItemColor(scheme.primary)
                .linkColor(scheme.primary)
                .isLinkUnderlined(false)
                .thematicBreakColor(scheme.outlineVariant)
        }
    }

    fun tableTheme(): TableTheme {
        val scheme = AppColorScheme.current
        return TableTheme.Builder()
            .tableBorderColor(scheme.outlineVariant)
            .tableBorderWidth(1.dpToPx())
            .tableCellPadding(8.dpToPx())
            .tableHeaderRowBackgroundColor(scheme.surfaceContainerLow)
            .build()
    }
}
