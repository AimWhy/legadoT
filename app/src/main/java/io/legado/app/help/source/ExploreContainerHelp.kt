package io.legado.app.help.source

import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 发现容器:分类 URL 解析与书籍数据磁盘缓存
 */
object ExploreContainerHelp {

    // lazy 保证 JVM 单测只调 resolve/json 函数时不触发 Android 依赖
    private val aCache by lazy { ACache.get("exploreContainerBooks") }

    /**
     * 优先按分类名在书源当前分类列表中匹配最新 URL(兼容 JS 动态生成的分类),
     * 同名分类以 URL 快照精确匹配优先;
     * 匹配不到或匹配项 URL 为空时回退到添加容器时的快照
     */
    fun resolveKindUrl(kinds: List<ExploreKind>, kindTitle: String, fallbackUrl: String): String {
        return kinds.firstOrNull { it.title == kindTitle && it.url == fallbackUrl }?.url
            ?: kinds.firstOrNull { it.title == kindTitle && !it.url.isNullOrBlank() }?.url
            ?: fallbackUrl
    }

    fun booksToJson(books: List<SearchBook>): String = GSON.toJson(books)

    fun booksFromJson(json: String?): List<SearchBook>? =
        GSON.fromJsonArray<SearchBook>(json).getOrNull()

    suspend fun getCachedBooks(containerId: Long): List<SearchBook>? =
        withContext(Dispatchers.IO) {
            booksFromJson(aCache.getAsString(containerId.toString()))
        }

    suspend fun putCachedBooks(containerId: Long, books: List<SearchBook>) {
        withContext(Dispatchers.IO) {
            aCache.put(containerId.toString(), booksToJson(books))
        }
    }

    suspend fun removeCache(containerId: Long) {
        withContext(Dispatchers.IO) {
            aCache.remove(containerId.toString())
        }
    }
}
