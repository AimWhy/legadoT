package io.legado.app.help.source

import com.google.gson.JsonObject
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 发现容器书籍缓存壳:带写入时间与页码;v1 裸数组按 time=0/page=1 兼容 */
data class CachedExploreBooks(
    val version: Int = 1,
    val time: Long = 0,
    val page: Int = 1,
    val books: List<SearchBook> = emptyList(),
)

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

    /** 缓存有效期,超过则进入页面时静默重拉(真机验收可临时调小) */
    const val CACHE_EXPIRE_MS = 24 * 60 * 60 * 1000L

    fun cachedToJson(cached: CachedExploreBooks): String = GSON.toJson(cached)

    /** 解析缓存壳;v1 裸数组包壳兼容;坏 JSON/缺 books 字段返回 null */
    fun cachedFromJson(json: String?): CachedExploreBooks? {
        if (json.isNullOrBlank()) return null
        if (json.trimStart().startsWith("[")) {
            return booksFromJson(json)?.let { CachedExploreBooks(time = 0, page = 1, books = it) }
        }
        return runCatching {
            val jsonObj = GSON.fromJson(json, JsonObject::class.java) ?: return null
            // books 字段必须存在(缺 books 字段返回 null)
            if (!jsonObj.has("books")) return null
            val shell: CachedExploreBooks? =
                GSON.fromJson(json, CachedExploreBooks::class.java)
            // GSON unsafe 分配不执行 Kotlin 默认值,缺字段是 JVM 零值
            @Suppress("SENSELESS_COMPARISON")
            if (shell == null || shell.books == null) return null
            shell.copy(page = shell.page.coerceAtLeast(1))
        }.getOrNull()
    }

    fun isExpired(time: Long, now: Long): Boolean = now - time > CACHE_EXPIRE_MS

    /** 相对时间;time<=0 返回 null(界面隐藏标签) */
    fun formatUpdateTime(time: Long, now: Long): String? {
        if (time <= 0) return null
        val diff = now - time
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3600_000L -> "${diff / 60_000L}分钟前"
            diff < 86400_000L -> "${diff / 3600_000L}小时前"
            else -> "${diff / 86400_000L}天前"
        }
    }

    /** 钉入反解:优先按当前 URL 精确匹配,再按标题,都不中用传入值当快照 */
    fun resolvePinKind(
        kinds: List<ExploreKind>,
        exploreName: String,
        exploreUrl: String,
    ): Pair<String, String> {
        val matched = kinds.firstOrNull { it.url == exploreUrl }
            ?: kinds.firstOrNull { it.title == exploreName && !it.url.isNullOrBlank() }
        return (matched?.title ?: exploreName) to (matched?.url ?: exploreUrl)
    }
}
