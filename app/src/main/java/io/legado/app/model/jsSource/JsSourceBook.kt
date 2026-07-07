package io.legado.app.model.jsSource

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.removeAllBookType
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * 纯JS源四段抓取(spec §5),与 WebBook 四入口同构;
 * Debug/CheckSource/换源/缓存只经 WebBook,分派后自动继承。
 */
object JsSourceBook {

    suspend fun searchAwait(
        bookSource: BookSource,
        key: String,
        page: Int? = 1,
        filter: ((name: String, author: String) -> Boolean)? = null,
    ): ArrayList<SearchBook> {
        val engine = JsSourceEngine(bookSource, coroutineContext)
        val json = engine.callFunction("search", listOf("key" to key, "page" to (page ?: 1)))
        val books = JsSourceMarshaller.parseSearchBooks(json, bookSource)
        if (filter != null) {
            books.removeAll { !filter(it.name, it.author) }
        }
        Debug.log(bookSource.bookSourceUrl, "◇JS源搜索完成,共${books.size}条")
        return books
    }

    suspend fun getBookInfoAwait(bookSource: BookSource, book: Book): Book {
        // 与 WebBook.getBookInfoAwait(:157-158)同款:先注默认 type,JS 可覆写
        book.removeAllBookType()
        book.addType(bookSource.getBookType())
        val engine = JsSourceEngine(bookSource, coroutineContext)
        if (engine.hasFunction("getBookInfo")) {
            val json = engine.callFunction("getBookInfo", listOf("book" to book))
            JsSourceMarshaller.mergeBookInfo(book, json, bookSource)
        }
        if (book.tocUrl.isBlank()) {
            book.tocUrl = book.bookUrl   // 声明式同款兜底:无目录页则详情页即目录页
        }
        return book
    }

    suspend fun getChapterListAwait(
        bookSource: BookSource,
        book: Book,
    ): Result<List<BookChapter>> {
        book.removeAllBookType()
        book.addType(bookSource.getBookType())
        val cc = coroutineContext
        return kotlin.runCatching {
            val engine = JsSourceEngine(bookSource, cc)
            val json = engine.callFunction("getChapters", listOf("book" to book))
            val chapters = JsSourceMarshaller.parseChapters(json, book, bookSource)
            if (chapters.isEmpty()) {
                throw NoStackTraceException("JS源目录为空")
            }
            chapters
        }.onFailure {
            cc.ensureActive()
        }
    }

    suspend fun getContentAwait(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        nextChapterUrl: String? = null,
        needSave: Boolean = true,
    ): String {
        if (bookChapter.isVolume && bookChapter.url.startsWith(bookChapter.title)) {
            // WebBook.getContentAwait(:310-313)同款:卷占位章不抓正文
            Debug.log(bookSource.bookSourceUrl, "⇒一级目录正文不解析")
            return bookChapter.tag ?: ""
        }
        val engine = JsSourceEngine(bookSource, coroutineContext)
        val content = engine.callFunction(
            "getContent",
            listOf("chapter" to bookChapter, "book" to book, "nextChapterUrl" to nextChapterUrl),
        )
        if (content.isNullOrBlank()) {
            throw NoStackTraceException("JS源正文为空")
        }
        if (needSave) {
            BookHelp.saveContent(bookSource, book, bookChapter, content)
        }
        return content
    }
}
