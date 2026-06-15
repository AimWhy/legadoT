package io.legado.app.ui.book.toc.rule

import io.legado.app.data.entities.TxtTocRule
import org.junit.Assert.assertEquals
import org.junit.Test

class TxtTocRuleFilterTest {

    private val rules = listOf(
        TxtTocRule(id = 1, name = "正文卷", example = "第一章"),
        TxtTocRule(id = 2, name = "VIP章节", example = "Chapter 1"),
        TxtTocRule(id = 3, name = "番外", example = null),
    )

    @Test
    fun `blank keyword returns the original list`() {
        assertEquals(listOf(1L, 2L, 3L), rules.filterByKeyword("   ").map { it.id })
    }

    @Test
    fun `matches by name case-insensitively`() {
        assertEquals(listOf(2L), rules.filterByKeyword("vip").map { it.id })
    }

    @Test
    fun `matches by example`() {
        assertEquals(listOf(1L), rules.filterByKeyword("第一").map { it.id })
    }

    @Test
    fun `null example is skipped and does not crash`() {
        assertEquals(emptyList<Long>(), rules.filterByKeyword("zzz").map { it.id })
    }
}
