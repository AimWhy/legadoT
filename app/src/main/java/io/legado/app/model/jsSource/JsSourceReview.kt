package io.legado.app.model.jsSource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import kotlin.coroutines.coroutineContext

/**
 * JS 源段评引擎:summary 与 detail 两阶段解析,递归处理嵌套 replies。
 * summary 返回 Map<段落序号, Pair<评论数, paraData>>;detail 返回评论列表+翻页 URL。
 */
object JsSourceReview {

    suspend fun getReviewSummaryAwait(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter
    ): Map<Int, Pair<Int, String>>? {
        val engine = JsSourceEngine(bookSource, coroutineContext)
        val json = engine.callFunctionIfExists(
            "getReviewSummary",
            listOf("chapter" to chapter, "book" to book)
        ) ?: return null

        val array = runCatching {
            GSON.fromJson(json, JsonArray::class.java)
        }.getOrNull() ?: return null

        val result = mutableMapOf<Int, Pair<Int, String>>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val paraIndex = runCatching { obj.get("paraIndex")?.asInt }.getOrNull() ?: return@forEach
            val count = runCatching { obj.get("count")?.asInt }.getOrNull() ?: 0
            if (count > 0) {
                val paraData = obj.optString("paraData") ?: paraIndex.toString()
                result[paraIndex] = count to paraData
            }
        }
        return result
    }

    suspend fun getReviewDetailAwait(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter,
        paraIndex: Int,
        paraData: String,
        page: Int
    ): Pair<List<ReviewDetailItem>, String?>? {
        val engine = JsSourceEngine(bookSource, coroutineContext)
        val json = engine.callFunction(
            "getReviewDetail",
            listOf(
                "chapter" to chapter,
                "book" to book,
                "paraIndex" to paraIndex,
                "paraData" to paraData,
                "page" to page
            )
        )

        val obj = runCatching {
            GSON.fromJson(json, JsonObject::class.java)
        }.getOrNull() ?: return null

        val itemsArray = obj.optArray("items") ?: return null
        val items = parseDetailItems(itemsArray)
        val nextPageUrl = obj.optString("nextPageUrl")

        return items to nextPageUrl
    }

    private fun parseDetailItems(array: JsonArray): List<ReviewDetailItem> {
        val result = mutableListOf<ReviewDetailItem>()
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val content = obj.optString("content")?.takeIf { it.isNotBlank() } ?: return@forEach
            val repliesArray = obj.optArray("replies")
            val replies = if (repliesArray != null) {
                parseDetailItems(repliesArray)
            } else {
                emptyList()
            }
            val item = ReviewDetailItem(
                id = obj.optString("id"),
                avatar = obj.optString("avatar"),
                name = obj.optString("name"),
                badge = obj.optString("badge"),
                content = content,
                replies = replies
            )
            result.add(item)
        }
        return result
    }

    data class ReviewDetailItem(
        val id: String?,
        val avatar: String?,
        val name: String?,
        val badge: String?,
        val content: String,
        val replies: List<ReviewDetailItem>
    )

    /**
     * 取字符串字段:键缺失返回 null;JSON null 经 GSON 解析为 JsonNull 元素,同样归一化为 null
     * (JsSourceMarshaller.mergeBookInfo 的 isJsonNull 守卫同源);非字符串型经 runCatching 兜底为 null。
     */
    private fun JsonObject.optString(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()
    }

    /**
     * 取数组字段:键缺失或 JSON null 返回 null;非数组型经 runCatching 兜底为 null。
     * 防止 {replies:null} 等常见形态下 (JsonArray) JsonNull 抛 ClassCastException。
     */
    private fun JsonObject.optArray(key: String): JsonArray? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element as? JsonArray }.getOrNull()
    }
}
