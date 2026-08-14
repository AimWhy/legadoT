package io.legado.app.help

import java.util.regex.Pattern

/**
 * 大小写不敏感查找全部匹配区间(闭区间 [start, end]),空白查询返回空。
 * 查询按字面量处理(正则元字符不生效),与帮助文档搜索行为一致。
 */
fun findTextRanges(text: String, query: String): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val pattern = Pattern.compile(
        Pattern.quote(query),
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )
    val matcher = pattern.matcher(text)
    val ranges = mutableListOf<IntRange>()
    while (matcher.find()) {
        ranges.add(matcher.start()..matcher.end() - 1)
    }
    return ranges
}
