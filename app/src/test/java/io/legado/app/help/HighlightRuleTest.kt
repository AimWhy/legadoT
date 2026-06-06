package io.legado.app.help

import io.legado.app.data.entities.HighlightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRuleTest {

    @Test
    fun empty_pattern_is_invalid() {
        assertFalse(HighlightRule(pattern = "").isValid())
    }

    @Test
    fun literal_pattern_is_valid() {
        assertTrue(HighlightRule(pattern = "张三", isRegex = false).isValid())
    }

    @Test
    fun good_regex_is_valid() {
        assertTrue(HighlightRule(pattern = "第[一二三]章", isRegex = true).isValid())
    }

    @Test
    fun bad_regex_is_invalid() {
        assertFalse(HighlightRule(pattern = "[", isRegex = true).isValid())
    }

    @Test
    fun style_round_trips_through_json() {
        val rule = HighlightRule(pattern = "x")
        rule.applyStyle(HighlightStyle(fill = 0x55FF0000.toInt(), bold = true))
        assertEquals(0x55FF0000.toInt(), rule.styleObj().fill)
        assertTrue(rule.styleObj().bold)
    }

    @Test
    fun display_name_falls_back_to_pattern() {
        assertEquals("关键词", HighlightRule(name = "", pattern = "关键词").getDisplayName())
        assertEquals("名字", HighlightRule(name = "名字", pattern = "关键词").getDisplayName())
    }
}
