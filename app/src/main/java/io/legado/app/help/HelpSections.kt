package io.legado.app.help

/**
 * 帮助文档章节切分:两级结构——H2 为一层,节内 H3 为二层;H2 不足两节回退 H3 单层,再不足返回空
 * (调用方隐藏目录入口)。围栏代码块内的 # 行不算标题;首标题前的非空引言归「简介」节;
 * 每节文本含标题行自身。
 */
object HelpSections {

    data class Section(
        val title: String,
        val text: String,
        val children: List<Section> = emptyList(),
    )

    fun parse(md: String, introTitle: String = "简介"): List<Section> {
        val lines = md.lines()
        val h2 = splitBy(lines, "## ", introTitle, withIntro = true)
        if (h2 != null) {
            return h2.map { section ->
                // 仅真实 H2 节(文本以自身标题行开头)继续按 H3 切子节;引言节不切
                val children = if (section.text.startsWith("## ")) {
                    splitBy(section.text.lines(), "### ", introTitle, withIntro = false) ?: emptyList()
                } else {
                    emptyList()
                }
                section.copy(children = children)
            }
        }
        return splitBy(lines, "### ", introTitle, withIntro = true) ?: emptyList()
    }

    /** 按 prefix 级标题切分;标题数不足 2 返回 null 交由下一级 */
    private fun splitBy(
        lines: List<String>,
        prefix: String,
        introTitle: String,
        withIntro: Boolean,
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
        if (withIntro && headingIdx.first() > 0) {
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
