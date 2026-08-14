package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpSectionsTest {

    @Test
    fun splitByH2WithIntro() {
        val md = """
            # 标题
            引言两行

            ## 甲
            甲内容
            ## 乙
            乙内容
        """.trimIndent()
        val sections = HelpSections.parse(md)
        assertEquals(listOf("简介", "甲", "乙"), sections.map { it.title })
        assertTrue(sections[0].text.contains("引言两行"))
        assertTrue(sections[1].text.startsWith("## 甲"))
        assertTrue(sections[1].text.contains("甲内容"))
        assertTrue(sections[2].text.endsWith("乙内容"))
    }

    @Test
    fun fencedHeadingIsNotBoundary() {
        val md = """
            ## 甲
            ```
            ## 假标题
            ```
            ## 乙
            内容
        """.trimIndent()
        val sections = HelpSections.parse(md)
        assertEquals(listOf("甲", "乙"), sections.map { it.title })
        assertTrue(sections[0].text.contains("## 假标题"))
    }

    @Test
    fun fallbackToH3() {
        val md = """
            # 文档
            ### 一
            内容一
            ### 二
            内容二
        """.trimIndent()
        val sections = HelpSections.parse(md)
        assertEquals(listOf("简介", "一", "二"), sections.map { it.title })
    }

    @Test
    fun h2PriorityOverH3() {
        val md = "## 甲\n### 子甲\n## 乙\n### 子乙"
        assertEquals(listOf("甲", "乙"), HelpSections.parse(md).map { it.title })
    }

    @Test
    fun tooFewHeadingsReturnsEmpty() {
        assertTrue(HelpSections.parse("# 只有标题\n正文").isEmpty())
        assertTrue(HelpSections.parse("## 单节\n正文").isEmpty())
    }

    @Test
    fun noPreambleNoIntroSection() {
        val md = "## 甲\n1\n## 乙\n2"
        assertEquals(listOf("甲", "乙"), HelpSections.parse(md).map { it.title })
    }

    @Test
    fun h3ChildrenNestedUnderH2() {
        val md = """
            ## 甲
            甲引言
            ### 子一
            内容一
            ### 子二
            内容二
            ## 乙
            乙内容
        """.trimIndent()
        val sections = HelpSections.parse(md)
        assertEquals(listOf("甲", "乙"), sections.map { it.title })
        assertEquals(listOf("子一", "子二"), sections[0].children.map { it.title })
        assertTrue(sections[0].children[0].text.startsWith("### 子一"))
        assertTrue(sections[0].children[1].text.contains("内容二"))
        assertTrue(sections[1].children.isEmpty())
    }

    @Test
    fun fencedH3IsNotChildBoundary() {
        val md = """
            ## 甲
            ### 子一
            ```
            ### 假子节
            ```
            ### 子二
            内容
            ## 乙
            乙内容
        """.trimIndent()
        val children = HelpSections.parse(md)[0].children
        assertEquals(listOf("子一", "子二"), children.map { it.title })
        assertTrue(children[0].text.contains("### 假子节"))
    }

    @Test
    fun singleH3StaysInlineWithoutChildren() {
        val md = "## 甲\n### 只有一个子节\n内容\n## 乙\n内容"
        val sections = HelpSections.parse(md)
        assertTrue(sections[0].children.isEmpty())
        assertTrue(sections[1].children.isEmpty())
    }

    @Test
    fun h3FallbackIsFlatWithoutChildren() {
        val md = "# 文档\n### 一\n内容一\n### 二\n内容二"
        val sections = HelpSections.parse(md)
        assertEquals(listOf("简介", "一", "二"), sections.map { it.title })
        assertTrue(sections.all { it.children.isEmpty() })
    }
}
