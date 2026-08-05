package io.legado.app.model.readaloud

import io.legado.app.data.entities.RoleCast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoleAnnotatorTest {

    @Test
    fun `content md5 tracks the joined read aloud text`() {
        val a = RoleAnnotator.contentMd5(listOf("第一段", "第二段"))
        assertEquals("同样的段落得到同样的键", a, RoleAnnotator.contentMd5(listOf("第一段", "第二段")))
        assertNotEquals("净化删字后必须换键", a, RoleAnnotator.contentMd5(listOf("第一段", "第二")))
        assertNotEquals("分段变化也要换键", a, RoleAnnotator.contentMd5(listOf("第一段第二段")))
    }

    @Test
    fun `annotation key tracks model prompt protocol and content`() {
        val paragraphs = listOf("第一段", "第二段")
        val key = RoleAnnotator.annotationKey(paragraphs, "model-a", "prompt-a")
        assertEquals(key, RoleAnnotator.annotationKey(paragraphs, "model-a", "prompt-a"))
        assertNotEquals(key, RoleAnnotator.annotationKey(paragraphs, "model-b", "prompt-a"))
        assertNotEquals(key, RoleAnnotator.annotationKey(paragraphs, "model-a", "prompt-b"))
        assertNotEquals(key, RoleAnnotator.annotationKey(listOf("第一段"), "model-a", "prompt-a"))
    }

    @Test
    fun `roles are recovered from segments without the narrator`() {
        val segments = listOf(
            Segment(0, 0, 2, RoleCast.NARRATOR),
            Segment(0, 2, 4, "林风"),
            Segment(1, 0, 2, "林风"),
            Segment(1, 2, 4, "苏眉"),
            Segment(2, 0, 2, "   ")
        )
        assertEquals(listOf("林风", "苏眉"), RoleAnnotator.rolesFrom(segments).map { it.name })
    }

    @Test
    fun `a narrator only chapter recovers no roles`() {
        val segments = listOf(Segment(0, 0, 2, RoleCast.NARRATOR))
        assertEquals(emptyList<RoleProfile>(), RoleAnnotator.rolesFrom(segments))
    }

    @Test
    fun `roles absent from the sanitized segments are dropped`() {
        val segments = listOf(
            Segment(0, 0, 2, RoleCast.NARRATOR),
            Segment(1, 0, 2, "林风")
        )
        val profiles = listOf(
            RoleProfile("林风", "male", "young"),
            RoleProfile("苏眉", "female", "young")
        )
        assertEquals(
            "净化把苏眉的段落还原成旁白后, 她不该留在角色表里",
            listOf(RoleProfile("林风", "male", "young")),
            RoleAnnotator.rolesIn(segments, profiles)
        )
    }

    @Test
    fun `a role speaking without a profile keeps its bare name`() {
        val segments = listOf(Segment(0, 0, 2, "苏眉"))
        assertEquals(listOf(RoleProfile("苏眉")), RoleAnnotator.rolesIn(segments, emptyList()))
    }

    @Test
    fun `a narrator only script yields no roles to cast`() {
        val segments = listOf(Segment(0, 0, 2, RoleCast.NARRATOR))
        val profiles = listOf(RoleProfile("林风", "male", "young"))
        assertEquals(emptyList<RoleProfile>(), RoleAnnotator.rolesIn(segments, profiles))
    }
}
