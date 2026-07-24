package io.legado.app.help

/**
 * 帮助文档章节切分:H2 优先,不足两节回退 H3,再不足返回空(调用方隐藏目录入口)。
 * 围栏代码块内的 # 行不算标题;首标题前的非空引言归「简介」节;每节文本含标题行自身。
 */
object HelpSections {

    data class Section(val title: String, val text: String)

    fun parse(md: String, introTitle: String = "简介"): List<Section> {
        val lines = md.lines()
        return splitBy(lines, "## ", introTitle)
            ?: splitBy(lines, "### ", introTitle)
            ?: emptyList()
    }

    /** 按 prefix 级标题切分;标题数不足 2 返回 null 交由下一级 */
    private fun splitBy(
        lines: List<String>,
        prefix: String,
        introTitle: String,
    ): List<Section>? {
        val headingIdx = mutableListOf<Int>()
        var inFence = false
        lines.forEachIndexed { i, line ->
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
            } else if (!inFence && line.startsWith(prefix) &&
                line.removePrefix(prefix).isNotBlank()
            ) {
                headingIdx.add(i)
            }
        }
        if (headingIdx.size < 2) return null
        val result = mutableListOf<Section>()
        if (headingIdx.first() > 0) {
            val intro = lines.subList(0, headingIdx.first()).joinToString("\n")
            if (intro.isNotBlank()) result.add(Section(introTitle, intro.trim()))
        }
        headingIdx.forEachIndexed { n, start ->
            val end = if (n + 1 < headingIdx.size) headingIdx[n + 1] else lines.size
            result.add(
                Section(
                    lines[start].removePrefix(prefix).trim(),
                    lines.subList(start, end).joinToString("\n").trim()
                )
            )
        }
        return result
    }
}
