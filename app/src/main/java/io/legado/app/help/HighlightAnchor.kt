package io.legado.app.help

/**
 * 手动划线的位置重锚(纯函数, 无 Android 依赖, JVM 可测)。
 *
 * 手动划线存的是章内绝对偏移, 而净化/替换/简繁转换/重新分段都在排版之前改动正文长度,
 * 于是删广告后被删点之后的划线整体漂移。以创建时保存的原文 [BookHighlight.bookText]
 * 在重排后的章节文本里就近搜回真实位置。
 *
 * 文本口径须与 [HighlightTextBuilder] 一致(偏移即章内 pos), bookText 口径见 getSelectedText。
 */
object HighlightAnchor {

    /** 重锚后的章内半开区间 [start, end) */
    data class Anchor(val start: Int, val end: Int)

    /**
     * @param text      重排后的整章文本(由 [HighlightTextBuilder] 构建)
     * @param start     创建时存下的章内起点
     * @param end       创建时存下的章内终点(半开)
     * @param bookText  创建时存下的划线原文; 空表示无从搜索(旧数据), 直接沿用存量偏移
     * @return 重锚区间; null = 原文已被净化删除, 调用方应隐藏该划线
     */
    fun reanchor(text: String, start: Int, end: Int, bookText: String): Anchor? {
        if (bookText.isEmpty()) return Anchor(start, end)
        if (text.isEmpty()) return null
        // 未漂移的常见情形: 原位就对得上, 省掉全章搜索
        if (start in 0..text.length - bookText.length &&
            text.startsWith(bookText, start)
        ) {
            return Anchor(start, start + bookText.length)
        }
        val hit = nearestOccurrence(text, bookText, start) ?: return null
        return Anchor(hit, hit + bookText.length)
    }

    /** 跳转落位点: 重锚后的起点; 无从重锚(原文已删/旧数据无原文)时沿用存量偏移 */
    fun jumpPos(text: String, start: Int, bookText: String): Int {
        if (bookText.isEmpty()) return start
        return reanchor(text, start, start, bookText)?.start ?: start
    }

    /** 取距 [target] 最近的一处出现; 前后等距时取靠前者 */
    private fun nearestOccurrence(text: String, pattern: String, target: Int): Int? {
        val before = text.lastIndexOf(pattern, target)
        val after = text.indexOf(pattern, target)
        return when {
            before < 0 -> if (after < 0) null else after
            after < 0 -> before
            target - before <= after - target -> before
            else -> after
        }
    }
}
