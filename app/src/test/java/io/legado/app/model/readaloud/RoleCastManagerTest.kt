package io.legado.app.model.readaloud

import io.legado.app.data.entities.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleCastManagerTest {

    private val maleYoung = VoiceRef(1, TtsVoice("m1", "云希", "male", "young"))
    private val maleOld = VoiceRef(1, TtsVoice("m2", "云健", "male", "old"))
    private val femaleYoung = VoiceRef(2, TtsVoice("f1", "晓晓", "female", "young"))
    private val pool = listOf(maleYoung, maleOld, femaleYoung)

    @Test
    fun `gender narrows the pool`() {
        assertEquals(
            femaleYoung,
            RoleCastManager.pickVoice(RoleProfile("苏眉", "female", "unknown"), pool, emptyMap())
        )
    }

    @Test
    fun `age narrows further within the matched gender`() {
        assertEquals(
            maleOld,
            RoleCastManager.pickVoice(RoleProfile("老王", "male", "old"), pool, emptyMap())
        )
    }

    @Test
    fun `an unmatchable gender keeps the whole pool rather than failing`() {
        val picked = RoleCastManager.pickVoice(
            RoleProfile("神秘人", "female", "child"),
            listOf(maleYoung, maleOld),
            emptyMap()
        )
        assertEquals(maleYoung, picked)
    }

    @Test
    fun `the least used voice wins so roles do not collide`() {
        val usage = mapOf(maleYoung.key to 2, maleOld.key to 1)
        assertEquals(
            maleOld,
            RoleCastManager.pickVoice(RoleProfile("甲", "male", "unknown"), pool, usage)
        )
    }

    @Test
    fun `ties break deterministically by key`() {
        val a = RoleCastManager.pickVoice(RoleProfile("甲", "male", "unknown"), pool, emptyMap())
        val b = RoleCastManager.pickVoice(RoleProfile("乙", "male", "unknown"), pool, emptyMap())
        assertEquals(a, b)
    }

    @Test
    fun `an empty pool yields null so the caller can fall back to the narrator`() {
        assertNull(RoleCastManager.pickVoice(RoleProfile("甲", "male", "young"), emptyList(), emptyMap()))
    }

    @Test
    fun `unknown gender does not narrow anything`() {
        assertEquals(
            maleYoung,
            RoleCastManager.pickVoice(RoleProfile("甲", null, null), pool, emptyMap())
        )
    }
}
