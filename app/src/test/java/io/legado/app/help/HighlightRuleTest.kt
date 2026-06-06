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

    @Test
    fun built_ins_are_two_disabled_valid_regex_rules() {
        val rules = HighlightRule.builtIns()
        assertEquals(2, rules.size)
        rules.forEach {
            assertFalse("内置规则默认应关闭", it.isEnabled)
            assertTrue("内置规则应为正则", it.isRegex)
            assertTrue("内置规则正则应合法", it.isValid())
            assertEquals("内置", it.group)
            assertTrue("内置规则应有名称", it.name.isNotBlank())
        }
    }

    @Test
    fun built_in_quote_rule_matches_dialogue_with_blue_fill() {
        val quote = HighlightRule.builtIns().first { it.name == "引号对话" }
        assertEquals(0x804FC3F7.toInt(), quote.styleObj().fill)
        // 应命中中文/全角引号内的对话, 非贪婪不跨多对
        assertTrue(Regex(quote.pattern).containsMatchIn("他说“你好”。"))
        assertTrue(Regex(quote.pattern).containsMatchIn("「早上好」"))
        val m = Regex(quote.pattern).find("“甲”和“乙”")!!
        assertEquals("“甲”", m.value)
    }

    @Test
    fun built_in_title_rule_matches_book_title_with_wavy_underline() {
        val title = HighlightRule.builtIns().first { it.name == "书名号" }
        val underline = title.styleObj().underline
        assertEquals(HighlightStyle.Kind.WAVY, underline?.kind)
        assertEquals(0xFFE53935.toInt(), underline?.color)
        val m = Regex(title.pattern).find("我在读《三体》和《活着》")!!
        assertEquals("《三体》", m.value)        // 非贪婪, 不吞到第二个书名号
    }
}
