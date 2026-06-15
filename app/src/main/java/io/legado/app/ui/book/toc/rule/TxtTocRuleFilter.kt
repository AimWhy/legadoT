package io.legado.app.ui.book.toc.rule

import io.legado.app.data.entities.TxtTocRule

/**
 * 按关键词过滤 TXT 目录规则：匹配规则名或示例(不区分大小写)。
 * 关键词去除首尾空白后为空时返回原列表。供阅读对话框与管理页共用。
 */
fun List<TxtTocRule>.filterByKeyword(keyword: String): List<TxtTocRule> {
    val key = keyword.trim()
    if (key.isEmpty()) return this
    return filter { rule ->
        rule.name.contains(key, ignoreCase = true) ||
            rule.example?.contains(key, ignoreCase = true) == true
    }
}
