package io.legado.app.model.readaloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePromptTest {

    @Test
    fun `chunks cover every paragraph exactly once`() {
        assertEquals(emptyList<IntRange>(), RolePrompt.chunks(0))
        assertEquals(listOf(0..59), RolePrompt.chunks(60))
        assertEquals(listOf(0..59, 60..60), RolePrompt.chunks(61))
        assertEquals(listOf(0..1, 2..3, 4..4), RolePrompt.chunks(5, batchSize = 2))
    }

    @Test
    fun `user prompt numbers paragraphs with absolute indices`() {
        val prompt = RolePrompt.buildUser(
            listOf("第零段", "第一段", "第二段"), 1..2, emptyList()
        )
        assertTrue(prompt.contains("[1] 第一段"))
        assertTrue(prompt.contains("[2] 第二段"))
        assertTrue("不该带上范围外的段落", !prompt.contains("[0]"))
    }

    @Test
    fun `known roles are carried into later chunks but the narrator is not`() {
        val withRoles = RolePrompt.buildUser(listOf("甲"), 0..0, listOf("林风", "旁白", " "))
        assertTrue(withRoles.contains("已知角色：林风"))
        assertTrue("旁白不必告诉模型", !withRoles.contains("旁白"))

        val noRoles = RolePrompt.buildUser(listOf("甲"), 0..0, emptyList())
        assertTrue(!noRoles.contains("已知角色"))
    }

    @Test
    fun `a well formed response parses into segments and roles`() {
        val json = """
            {"roles":[{"name":"林风","gender":"male","age":"young"}],
             "segments":[{"p":0,"s":0,"e":6,"r":"旁白"},{"p":0,"s":6,"e":8,"r":"林风"}]}
        """.trimIndent()
        val script = RolePrompt.parse(json, 0..0)!!
        assertEquals(listOf(RoleProfile("林风", "male", "young")), script.roles)
        assertEquals(Segment(0, 0, 6, "旁白"), script.segments[0])
        assertEquals(Segment(0, 6, 8, "林风"), script.segments[1])
    }

    @Test
    fun `segments outside the requested chunk are dropped`() {
        val json = """{"segments":[{"p":3,"s":0,"e":2,"r":"甲"},{"p":9,"s":0,"e":2,"r":"乙"}]}"""
        val script = RolePrompt.parse(json, 3..5)!!
        assertEquals(1, script.segments.size)
        assertEquals(3, script.segments[0].p)
    }

    @Test
    fun `malformed json yields null and an empty object yields an empty script`() {
        assertNull(RolePrompt.parse("not json at all", 0..0))
        val empty = RolePrompt.parse("{}", 0..0)!!
        assertTrue(empty.segments.isEmpty())
        assertTrue(empty.roles.isEmpty())
    }

    @Test
    fun `explicit nulls in string fields do not crash the parser`() {
        val json = """
            {"roles":[{"name":null,"gender":null,"age":null},{"name":"林风"}],
             "segments":[{"p":0,"s":0,"e":2,"r":null},null]}
        """.trimIndent()
        val script = RolePrompt.parse(json, 0..0)!!
        assertEquals(listOf("林风"), script.roles.map { it.name })
        assertEquals(listOf(Segment(0, 0, 2, "")), script.segments)
    }

    @Test
    fun `merge concatenates segments in order and dedupes roles by name`() {
        val a = RoleScript(
            listOf(Segment(1, 0, 2, "甲")),
            listOf(RoleProfile("林风", "male", "young"))
        )
        val b = RoleScript(
            listOf(Segment(0, 0, 2, "乙")),
            listOf(RoleProfile("林风", "female", "old"), RoleProfile("苏眉", "female", "young"))
        )
        val merged = RolePrompt.merge(listOf(a, b))
        assertEquals(listOf(Segment(0, 0, 2, "乙"), Segment(1, 0, 2, "甲")), merged.segments)
        assertEquals(listOf("林风", "苏眉"), merged.roles.map { it.name })
        assertEquals("先出现的画像胜出", "male", merged.roles[0].gender)
    }
}
