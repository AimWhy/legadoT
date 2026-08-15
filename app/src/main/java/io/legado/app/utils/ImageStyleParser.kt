package io.legado.app.utils

import io.legado.app.model.analyzeRule.AnalyzeUrl
import java.util.Locale

/**
 * 图片内联样式解析。
 *
 * 图片URL可以带内联选项,如 <img src="https://xx/a.svg,{"style":"TEXT","js":"..."}">,
 * 其中的 style 用于单独控制这一张图片的排版样式,优先于书源/阅读界面的全局图片样式。
 *
 * style 取值:
 * - 关键词(不区分大小写):
 *   DEFAULT 默认(居中,超出屏幕等比缩小)
 *   FULL    最大宽度
 *   SINGLE  单图整页
 *   TEXT    行内嵌入(与文字同行)
 * - CSS 尺寸: width/height 与 %/px(或dp) 的组合,如 width:50%、width:200px、height:30%、
 *   width:50%;height:auto。只给宽或高时,另一维按图片原始比例补全。
 */
object ImageStyleParser {

    sealed class ImageStyle {

        /** 默认:居中,超出屏幕等比缩小 */
        object Default : ImageStyle()

        /** 最大宽度 */
        object Full : ImageStyle()

        /** 单图整页 */
        object Single : ImageStyle()

        /** 行内嵌入(与文字同行) */
        object Text : ImageStyle()

        /** 显式尺寸 */
        data class Size(
            val widthPercent: Float? = null,
            val heightPercent: Float? = null,
            val widthPx: Float? = null,
            val heightPx: Float? = null
        ) : ImageStyle() {
            val hasWidth: Boolean get() = widthPercent != null || widthPx != null
            val hasHeight: Boolean get() = heightPercent != null || heightPx != null
            val isEmpty: Boolean get() = !hasWidth && !hasHeight
        }

        /** 关键词映射,显式尺寸返回null(回落到全局样式) */
        val keyword: String?
            get() = when (this) {
                is Default -> "DEFAULT"
                is Full -> "FULL"
                is Single -> "SINGLE"
                is Text -> "TEXT"
                is Size -> null
            }

        companion object {

            /**
             * 从图片src的 ,{...} 选项里解析内联样式,没有style选项时返回null
             */
            fun fromSrc(src: String): ImageStyle? {
                val matcher = AnalyzeUrl.paramPattern.matcher(src)
                if (!matcher.find()) return null
                val optionStr = src.substring(matcher.end())
                val style = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(optionStr)
                    .getOrNull()?.getStyle() ?: return null
                return parse(style)
            }

            /**
             * 解析style字符串,空白或无法识别时返回null
             */
            fun parse(style: String?): ImageStyle? {
                if (style.isNullOrBlank()) return null
                when (style.trim().uppercase(Locale.ROOT)) {
                    "DEFAULT", "AUTO" -> return Default
                    "FULL" -> return Full
                    "SINGLE" -> return Single
                    "TEXT" -> return Text
                }
                return parseSize(style)?.takeIf { !it.isEmpty }
            }

            private fun parseSize(style: String): Size? {
                var widthPercent: Float? = null
                var heightPercent: Float? = null
                var widthPx: Float? = null
                var heightPx: Float? = null
                for (declaration in style.split(';')) {
                    val index = declaration.indexOf(':')
                    if (index < 0) continue
                    val key = declaration.substring(0, index).trim().lowercase(Locale.ROOT)
                    if (key != "width" && key != "height") continue
                    val value = declaration.substring(index + 1).trim()
                    if (value.isEmpty() || value.equals("auto", true)) continue
                    val number = parseNumber(value) ?: continue
                    val isPercent = value.endsWith("%")
                    when {
                        key == "width" && isPercent -> widthPercent = number
                        key == "width" -> widthPx = number
                        key == "height" && isPercent -> heightPercent = number
                        else -> heightPx = number
                    }
                }
                return Size(widthPercent, heightPercent, widthPx, heightPx)
            }

            /**
             * 解析CSS数值: 50% -> 50, 200px/200dp/200 -> 200, 否则null
             */
            private fun parseNumber(value: String): Float? {
                return when {
                    value.endsWith("%") -> value.dropLast(1).trim().toFloatOrNull()
                    value.endsWith("px", true) || value.endsWith("dp", true) ->
                        value.dropLast(2).trim().toFloatOrNull()

                    else -> value.toFloatOrNull()
                }
            }
        }
    }
}
