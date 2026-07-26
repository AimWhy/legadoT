package io.legado.app.web.mcp

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource
import org.htmlunit.corejs.javascript.Undefined
import kotlin.math.abs

/**
 * MCP 工具的纯文本辅助:格式识别/摘要裁剪/截断/求值结果渲染。
 * 输出文本是工具返回契约的一部分,改动需同步 McpToolServer 的 description。
 */
object McpFormat {

    const val TRUNCATE_LIMIT = 100_000

    // 2^53,Double 精确表示整数的上界
    private const val MAX_EXACT_DOUBLE = 9.007199254740992E15

    private val prettyGson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun detectFormat(source: String): String {
        val first = source.trimStart().firstOrNull()
        return if (first == '{' || first == '[') "json" else "js"
    }

    fun summarizeSources(sources: List<BookSource>, search: String?): List<Map<String, Any>> {
        val summaries = sources.map {
            mapOf(
                "bookSourceName" to it.bookSourceName,
                "bookSourceUrl" to it.bookSourceUrl,
                "bookSourceGroup" to (it.bookSourceGroup ?: ""),
                "enabled" to it.enabled,
                "isJsSource" to !it.mainJs.isNullOrEmpty(),
            )
        }
        if (search.isNullOrEmpty()) return summaries
        val q = search.lowercase()
        return summaries.filter {
            (it["bookSourceName"] as String).lowercase().contains(q) ||
                (it["bookSourceUrl"] as String).lowercase().contains(q)
        }
    }

    fun toPrettyJson(value: Any): String = prettyGson.toJson(value)

    fun prettyJson(json: String): String = prettyGson.toJson(JsonParser.parseString(json))

    fun truncate(text: String, limit: Int = TRUNCATE_LIMIT): String {
        if (text.length <= limit) return text
        return text.take(limit) + "\n…[已截断,原文 ${text.length} 字符]"
    }

    /**
     * 校验汇总。respondTime 语义:校验失败时 Debug 写入 timeout+耗时,故 > timeoutMs 即失败。
     */
    fun renderCheckSummary(
        sources: List<BookSource>,
        messages: Map<String, String>,
        timeoutMs: Long,
    ): String {
        val bad = mutableListOf<String>()
        val good = mutableListOf<String>()
        for (s in sources) {
            val invalid = s.getInvalidGroupNames()
            val msg = messages[s.bookSourceUrl].orEmpty()
            val errorComment = s.bookSourceComment
                ?.lineSequence()
                ?.firstOrNull { it.startsWith("// Error: ") }
                .orEmpty()
            if (invalid.isNotEmpty() || s.respondTime > timeoutMs) {
                val reason = listOf(invalid, errorComment, msg)
                    .filter { it.isNotEmpty() }
                    .joinToString(" | ")
                bad += "✗ ${s.bookSourceName}(${s.bookSourceUrl}):$reason"
            } else {
                good += "✓ ${s.bookSourceName}(${s.bookSourceUrl})" +
                    if (msg.isNotEmpty()) " $msg" else ""
            }
        }
        return buildString {
            appendLine("坏源 ${bad.size}/${sources.size}:")
            if (bad.isEmpty()) appendLine("(无)") else bad.forEach { appendLine(it) }
            appendLine()
            appendLine("正常 ${good.size}/${sources.size}:")
            if (good.isEmpty()) appendLine("(无)") else good.forEach { appendLine(it) }
        }.trimEnd()
    }

    fun renderEvalResult(result: Any?): String = when (result) {
        null -> "null"
        is Undefined -> "undefined"
        is CharSequence -> result.toString()
        is Number -> foldIntegralDouble(result).toString()
        is Boolean, is Char -> result.toString()
        is Map<*, *>, is List<*> -> runCatching { toPrettyJson(normalizeJsValue(result)!!) }
            .getOrElse { "$result (${result.javaClass.simpleName})" }
        else -> "$result (${result.javaClass.simpleName})"
    }

    private fun normalizeJsValue(v: Any?): Any? = when (v) {
        null -> null
        is Undefined -> "undefined"
        is CharSequence -> v.toString()
        is Number -> foldIntegralDouble(v)
        is Boolean -> v
        is Map<*, *> -> v.entries.associate { (k, value) -> k.toString() to normalizeJsValue(value) }
        is List<*> -> v.map { normalizeJsValue(it) }
        else -> v.toString()
    }

    private fun foldIntegralDouble(v: Number): Number =
        if (v is Double && v.isFinite() && v % 1.0 == 0.0 && abs(v) <= MAX_EXACT_DOUBLE) {
            v.toLong()
        } else {
            v
        }
}
