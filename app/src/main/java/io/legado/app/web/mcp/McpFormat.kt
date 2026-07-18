package io.legado.app.web.mcp

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource

/**
 * MCP 工具的纯文本辅助:格式识别/摘要裁剪/截断。
 * 输出文本是工具返回契约的一部分,改动需同步 McpToolServer 的 description。
 */
object McpFormat {

    const val TRUNCATE_LIMIT = 100_000

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
}
