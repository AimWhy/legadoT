package io.legado.app.help.source

import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreContainerHelpTest {

    private val kinds = listOf(
        ExploreKind("玄幻", "https://a.com/xuanhuan/{{page}}"),
        ExploreKind("都市", "https://a.com/dushi/{{page}}"),
        ExploreKind("分组标题", null),
    )

    @Test
    fun resolve_prefers_current_kind_url_by_title() {
        val url = ExploreContainerHelp.resolveKindUrl(kinds, "玄幻", "https://a.com/old")
        assertEquals("https://a.com/xuanhuan/{{page}}", url)
    }

    @Test
    fun resolve_falls_back_to_snapshot_when_title_missing() {
        val url = ExploreContainerHelp.resolveKindUrl(kinds, "已删除分类", "https://a.com/old")
        assertEquals("https://a.com/old", url)
    }

    @Test
    fun resolve_falls_back_when_matched_kind_has_blank_url() {
        val url = ExploreContainerHelp.resolveKindUrl(kinds, "分组标题", "https://a.com/old")
        assertEquals("https://a.com/old", url)
    }

    @Test
    fun resolve_prefers_exact_url_match_among_duplicate_titles() {
        val dup = listOf(
            ExploreKind("更多", "https://a.com/fantasy/more"),
            ExploreKind("更多", "https://a.com/city/more"),
        )
        val url = ExploreContainerHelp.resolveKindUrl(dup, "更多", "https://a.com/city/more")
        assertEquals("https://a.com/city/more", url)
    }

    @Test
    fun books_json_round_trip() {
        val books = listOf(
            SearchBook(
                bookUrl = "https://a.com/b/1", origin = "https://a.com",
                name = "斗破苍穹", author = "天蚕土豆",
                coverUrl = "https://a.com/c/1.jpg", intro = "简介"
            ),
            SearchBook(
                bookUrl = "https://a.com/b/2", origin = "https://a.com",
                name = "完美世界", author = "辰东"
            ),
        )
        val json = ExploreContainerHelp.booksToJson(books)
        val parsed = ExploreContainerHelp.booksFromJson(json)
        assertEquals(2, parsed!!.size)
        assertEquals("斗破苍穹", parsed[0].name)
        assertEquals("https://a.com/b/2", parsed[1].bookUrl)
    }

    @Test
    fun books_from_invalid_json_returns_null() {
        assertNull(ExploreContainerHelp.booksFromJson("not json"))
        assertNull(ExploreContainerHelp.booksFromJson(null))
    }

    @Test
    fun books_from_empty_json_array_returns_empty_list_not_null() {
        val parsed = ExploreContainerHelp.booksFromJson("[]")
        assertEquals(0, parsed!!.size)
    }

    // ===== 缓存壳 =====

    @Test
    fun cached_round_trip() {
        val books = listOf(
            SearchBook(
                bookUrl = "https://a.com/b/1", origin = "https://a.com",
                name = "斗破苍穹", author = "天蚕土豆"
            )
        )
        val json = ExploreContainerHelp.cachedToJson(
            CachedExploreBooks(time = 123L, page = 5, books = books)
        )
        val parsed = ExploreContainerHelp.cachedFromJson(json)!!
        assertEquals(123L, parsed.time)
        assertEquals(5, parsed.page)
        assertEquals("斗破苍穹", parsed.books[0].name)
    }

    @Test
    fun cached_from_legacy_bare_array_defaults_time0_page1() {
        val books = listOf(
            SearchBook(bookUrl = "https://a.com/b/1", origin = "https://a.com", name = "完美世界")
        )
        val legacyJson = ExploreContainerHelp.booksToJson(books)
        val parsed = ExploreContainerHelp.cachedFromJson(legacyJson)!!
        assertEquals(0L, parsed.time)
        assertEquals(1, parsed.page)
        assertEquals("完美世界", parsed.books[0].name)
    }

    @Test
    fun cached_from_invalid_json_returns_null() {
        assertNull(ExploreContainerHelp.cachedFromJson(null))
        assertNull(ExploreContainerHelp.cachedFromJson(""))
        assertNull(ExploreContainerHelp.cachedFromJson("not json"))
        // GSON unsafe 分配不执行 Kotlin 默认值,缺 books 字段是 null,必须判掉
        assertNull(ExploreContainerHelp.cachedFromJson("{\"time\":1}"))
    }

    @Test
    fun cached_page_below_1_is_clamped() {
        val parsed = ExploreContainerHelp.cachedFromJson("{\"time\":1,\"page\":0,\"books\":[]}")!!
        assertEquals(1, parsed.page)
    }

    // ===== 过期判断 =====

    @Test
    fun expired_when_time_zero_or_older_than_24h() {
        val now = 1_000_000_000_000L
        assertTrue(ExploreContainerHelp.isExpired(0L, now))
        assertTrue(ExploreContainerHelp.isExpired(now - 25 * 3600_000L, now))
        assertFalse(ExploreContainerHelp.isExpired(now - 23 * 3600_000L, now))
    }

    // ===== 相对时间 =====

    @Test
    fun format_update_time() {
        val now = 1_000_000_000_000L
        assertNull(ExploreContainerHelp.formatUpdateTime(0L, now))
        assertEquals("刚刚", ExploreContainerHelp.formatUpdateTime(now - 30_000L, now))
        assertEquals("5分钟前", ExploreContainerHelp.formatUpdateTime(now - 5 * 60_000L, now))
        assertEquals("3小时前", ExploreContainerHelp.formatUpdateTime(now - 3 * 3600_000L, now))
        assertEquals("2天前", ExploreContainerHelp.formatUpdateTime(now - 2 * 86400_000L, now))
        // 时钟回拨(time 在未来)按刚刚
        assertEquals("刚刚", ExploreContainerHelp.formatUpdateTime(now + 60_000L, now))
    }

    // ===== 钉入反解 =====

    @Test
    fun pin_resolve_prefers_url_match() {
        val (title, url) = ExploreContainerHelp.resolvePinKind(
            kinds, "改名后的玄幻", "https://a.com/xuanhuan/{{page}}"
        )
        assertEquals("玄幻", title)
        assertEquals("https://a.com/xuanhuan/{{page}}", url)
    }

    @Test
    fun pin_resolve_falls_back_to_title_match() {
        val (title, url) = ExploreContainerHelp.resolvePinKind(
            kinds, "都市", "https://a.com/resolved/dushi"
        )
        assertEquals("都市", title)
        assertEquals("https://a.com/dushi/{{page}}", url)
    }

    @Test
    fun pin_resolve_falls_back_to_snapshot() {
        val (title, url) = ExploreContainerHelp.resolvePinKind(
            kinds, "未知分类", "https://a.com/unknown"
        )
        assertEquals("未知分类", title)
        assertEquals("https://a.com/unknown", url)
    }
}
