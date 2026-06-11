package io.legado.app.help.source

import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
