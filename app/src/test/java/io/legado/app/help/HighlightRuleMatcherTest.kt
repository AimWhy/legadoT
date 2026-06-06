package io.legado.app.help

import io.legado.app.help.HighlightRuleMatcher.Rule
import io.legado.app.help.HighlightRuleMatcher.RuleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRuleMatcherTest {

    private val redFill = HighlightStyle(fill = 0xFFFF0000.toInt())

    private fun lit(id: Long, p: String) = Rule(id, p, false, redFill)
    private fun re(id: Long, p: String) = Rule(id, p, true, redFill)

    @Test
    fun literal_finds_all_non_overlapping_occurrences() {
        val m = HighlightRuleMatcher.match("aXaXa", listOf(lit(1, "aX")))
        assertEquals(listOf(0 to 2, 2 to 4), m.map { it.start to it.end })
        assertTrue(m.all { it.ruleId == 1L })
    }

    @Test
    fun literal_no_match_returns_empty() {
        assertTrue(HighlightRuleMatcher.match("hello", listOf(lit(1, "zzz"))).isEmpty())
    }

    @Test
    fun regex_finds_matches_with_correct_offsets() {
        val m = HighlightRuleMatcher.match("第1章 第22章", listOf(re(7, "第\\d+章")))
        assertEquals(listOf(0 to 3, 4 to 8), m.map { it.start to it.end })
        assertTrue(m.all { it.ruleId == 7L })
    }

    @Test
    fun zero_width_regex_does_not_loop_and_yields_no_match() {
        val m = HighlightRuleMatcher.match("bbb", listOf(re(1, "a*")))
        assertTrue(m.isEmpty())
    }

    @Test
    fun invalid_regex_is_skipped_not_thrown() {
        val m = HighlightRuleMatcher.match("anything", listOf(re(1, "[")))
        assertTrue(m.isEmpty())
    }

    @Test
    fun empty_pattern_skipped() {
        assertTrue(HighlightRuleMatcher.match("abc", listOf(lit(1, ""))).isEmpty())
    }

    @Test
    fun multiple_rules_overlap_each_keeps_its_own_match() {
        val rules = listOf(lit(1, "abc"), lit(2, "bcd"))
        val m = HighlightRuleMatcher.match("abcd", rules)
        assertTrue(m.any { it.ruleId == 1L && it.start == 0 && it.end == 3 })
        assertTrue(m.any { it.ruleId == 2L && it.start == 1 && it.end == 4 })
    }

    @Test
    fun empty_text_or_rules_returns_empty() {
        assertTrue(HighlightRuleMatcher.match("", listOf(lit(1, "a"))).isEmpty())
        assertTrue(HighlightRuleMatcher.match("abc", emptyList()).isEmpty())
    }
}
