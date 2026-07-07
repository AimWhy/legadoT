package io.legado.app.model.jsSource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils

/**
 * JS 返回 JSON → 实体(spec §3)。
 * 白名单+引擎注入:origin 族/主键不受 JS 影响;type=先注默认后可覆写(仅合法 BookType 位值)。
 */
object JsSourceMarshaller {

    /**
     * Debug.log 在 BuildConfig.DEBUG 为真时无条件调用 android.util.Log.d;
     * 裸 JVM 单元测试(无 Robolectric/MockK)下该方法未 mock 会抛 RuntimeException。
     * 生产/插桩测试环境下 Log.d 正常返回,此处吞异常不改变任何可观察行为。
     */
    private fun debugLog(sourceUrl: String, msg: String) {
        runCatching { Debug.log(sourceUrl, msg) }
    }

    /** 仅接受 BookType 已知源位组合(text/audio/image/webFile),非法返回 null */
    fun validateBookType(raw: Int): Int? {
        if (raw == 0) return null
        if (raw and BookType.allBookType.inv() != 0) return null
        return raw
    }

    private fun resolveType(obj: JsonObject, source: BookSource): Int {
        if (obj.has("type")) {
            val raw = runCatching { obj.get("type").asInt }.getOrNull()
            if (raw != null) {
                validateBookType(raw)?.let { return it }
            }
            debugLog(source.bookSourceUrl, "⇒type 非法(须用 BookType 位值,如 text=8/audio=32),回退源类型")
        }
        return source.getBookType()
    }

    fun parseSearchBooks(json: String?, source: BookSource): ArrayList<SearchBook> {
        val result = arrayListOf<SearchBook>()
        if (json.isNullOrBlank()) return result
        val array = runCatching { GSON.fromJson(json, JsonArray::class.java) }.getOrNull()
            ?: throw NoStackTraceException("search 返回值不是数组")
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val book = runCatching { GSON.fromJson(obj, SearchBook::class.java) }.getOrNull()
                ?: return@forEach
            if (book.name.isBlank() || book.bookUrl.isBlank()) {
                debugLog(source.bookSourceUrl, "⇒丢弃缺少 name/bookUrl 的搜索条目")
                return@forEach
            }
            book.origin = source.bookSourceUrl
            book.originName = source.bookSourceName
            book.originOrder = source.customOrder
            book.type = resolveType(obj, source)
            result.add(book)
        }
        return result
    }

    fun mergeBookInfo(book: Book, json: String?, source: BookSource) {
        if (json.isNullOrBlank()) return
        val obj = runCatching { GSON.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: throw NoStackTraceException("getBookInfo 返回值不是对象")
        obj.entrySet().forEach { (key, value) ->
            if (value.isJsonNull) return@forEach
            when (key) {
                "name" -> book.name = value.asString
                "author" -> book.author = value.asString
                "intro" -> book.intro = value.asString
                "coverUrl" -> book.coverUrl = value.asString
                "kind" -> book.kind = value.asString
                "wordCount" -> book.wordCount = value.asString
                "latestChapterTitle" -> book.latestChapterTitle = value.asString
                "tocUrl" -> book.tocUrl = value.asString
                "variable" -> book.variable = value.asString
                "type" -> runCatching { value.asInt }.getOrNull()?.let { raw ->
                    validateBookType(raw)?.let { book.type = it } ?: debugLog(
                        source.bookSourceUrl, "⇒type 非法(须用 BookType 位值),忽略",
                    )
                }
                // 其余键(含 bookUrl 主键、dur*/custom* 用户态)一律忽略(spec §3 白名单)
            }
        }
    }

    fun parseChapters(json: String?, book: Book, source: BookSource): List<BookChapter> {
        val chapters = arrayListOf<BookChapter>()
        if (json.isNullOrBlank()) return chapters
        val array = runCatching { GSON.fromJson(json, JsonArray::class.java) }.getOrNull()
            ?: throw NoStackTraceException("getChapters 返回值不是数组")
        array.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            val chapter = runCatching { GSON.fromJson(obj, BookChapter::class.java) }.getOrNull()
                ?: return@forEach
            if (chapter.title.isBlank() || chapter.url.isBlank()) {
                debugLog(source.bookSourceUrl, "⇒丢弃缺少 title/url 的章节")
                return@forEach
            }
            chapter.url = NetworkUtils.getAbsoluteURL(book.tocUrl, chapter.url)
            chapter.bookUrl = book.bookUrl
            chapter.baseUrl = book.tocUrl
            chapter.index = chapters.size
            chapters.add(chapter)
        }
        return chapters
    }
}
