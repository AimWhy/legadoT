package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightAnchorTest {

    @Test
    fun `undrifted highlight keeps its stored offsets`() {
        val text = "主角拔剑出鞘"
        val a = HighlightAnchor.reanchor(text, 0, 2, "主角")
        assertEquals(HighlightAnchor.Anchor(0, 2), a)
    }

    @Test
    fun `drifted highlight follows the text after ad removal`() {
        // 创建时正文为"【广告】主角拔剑出鞘", 划线"主角"存了 [4,6)
        // 净化删掉"【广告】"后, 正文变短, 应重锚到 [0,2)
        val purified = "主角拔剑出鞘"
        val a = HighlightAnchor.reanchor(purified, 4, 6, "主角")
        assertEquals(HighlightAnchor.Anchor(0, 2), a)
    }

    @Test
    fun `deleted text yields null so the caller can hide it`() {
        val purified = "拔剑出鞘"
        assertNull(HighlightAnchor.reanchor(purified, 4, 6, "主角"))
    }

    @Test
    fun `repeated phrase resolves to the occurrence nearest the stored position`() {
        //           0123456789
        val text = "剑光剑光剑光剑光"
        // stored 起点 4 → 命中第 4 位那一处, 而非第 0 位
        assertEquals(HighlightAnchor.Anchor(4, 6), HighlightAnchor.reanchor(text, 4, 6, "剑光"))
        // stored 起点 5 → 距 4 与 6 各 1, 取靠前者
        assertEquals(HighlightAnchor.Anchor(4, 6), HighlightAnchor.reanchor(text, 5, 7, "剑光"))
        // 广告删除使 stored 偏后 → 就近回退到 6
        assertEquals(HighlightAnchor.Anchor(6, 8), HighlightAnchor.reanchor(text, 7, 9, "剑光"))
    }

    @Test
    fun `stored position beyond text length still re-anchors`() {
        val text = "主角拔剑"
        assertEquals(HighlightAnchor.Anchor(2, 4), HighlightAnchor.reanchor(text, 99, 101, "拔剑"))
    }

    @Test
    fun `empty bookText falls back to stored offsets`() {
        // 无原文可搜(旧数据) → 不隐藏, 保留原偏移
        val text = "主角拔剑"
        assertEquals(HighlightAnchor.Anchor(1, 3), HighlightAnchor.reanchor(text, 1, 3, ""))
    }

    @Test
    fun `empty text hides a highlight that has bookText`() {
        assertNull(HighlightAnchor.reanchor("", 0, 2, "主角"))
    }

    @Test
    fun `multiline bookText spanning a paragraph end is matched`() {
        // getSelectedText 在段末补 "\n", HighlightTextBuilder 同口径
        val text = "第一段\n第二段\n"
        assertEquals(HighlightAnchor.Anchor(2, 6), HighlightAnchor.reanchor(text, 2, 6, "段\n第二"))
    }

    @Test
    fun `user edited bookText that no longer matches hides the highlight`() {
        // 备注弹窗允许改 bookText; 改成正文里没有的内容 → 无从重锚 → 隐藏
        val text = "主角拔剑出鞘"
        assertNull(HighlightAnchor.reanchor(text, 0, 2, "改成了别的字"))
    }
}
