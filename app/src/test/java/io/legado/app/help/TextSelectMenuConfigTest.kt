package io.legado.app.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSelectMenuConfigTest {

    @Test
    fun defaultHasExpectedPartition() {
        val c = TextSelectMenuConfig.default()
        assertEquals(listOf("replace", "copy", "bookmark", "highlight", "aloud"), c.bar)
        assertEquals(listOf("dict", "search", "browser", "share"), c.more)
    }

    @Test
    fun jsonRoundTrip() {
        val c = TextSelectMenuConfig(listOf("copy", "highlight"), listOf("share"))
        assertEquals(c, TextSelectMenuConfig.fromJson(c.toJson()))
    }

    @Test
    fun fromJsonBlankReturnsDefault() {
        assertEquals(TextSelectMenuConfig.default(), TextSelectMenuConfig.fromJson(null))
        assertEquals(TextSelectMenuConfig.default(), TextSelectMenuConfig.fromJson("   "))
    }

    @Test
    fun fromJsonMalformedReturnsDefault() {
        assertEquals(TextSelectMenuConfig.default(), TextSelectMenuConfig.fromJson("{not json"))
    }

    @Test
    fun fromJsonMissingFieldTolerated() {
        val c = TextSelectMenuConfig.fromJson("{\"bar\":[\"copy\"]}")
        assertEquals(listOf("copy"), c.bar)
        assertEquals(emptyList<String>(), c.more)
    }

    @Test
    fun normalizedDropsUnknownKeys() {
        val c = TextSelectMenuConfig(listOf("copy", "bogus"), listOf("share")).normalized()
        assertTrue("bogus" !in c.bar && "bogus" !in c.more)
        assertEquals(listOf("copy"), c.bar)
    }

    @Test
    fun normalizedDedupsAcrossZones() {
        val c = TextSelectMenuConfig(listOf("copy"), listOf("copy", "share")).normalized()
        assertEquals(listOf("copy"), c.bar)
        assertEquals(0, c.more.count { it == "copy" })
    }

    @Test
    fun normalizedAppendsMissingKnownKeysToMore() {
        val c = TextSelectMenuConfig(listOf("copy"), listOf("share")).normalized()
        val all = c.bar + c.more
        assertEquals(TextSelectMenuConfig.ALL_KEYS.toSet(), all.toSet())
        assertEquals(9, all.size)
        assertTrue(
            c.more.containsAll(
                listOf("replace", "bookmark", "highlight", "aloud", "dict", "search", "browser")
            )
        )
    }

    @Test
    fun migrateFromTruePutsAllInBar() {
        val c = TextSelectMenuConfig.migrateFrom(true)
        assertEquals(TextSelectMenuConfig.ALL_KEYS, c.bar)
        assertEquals(emptyList<String>(), c.more)
    }

    @Test
    fun migrateFromFalseReturnsDefault() {
        assertEquals(TextSelectMenuConfig.default(), TextSelectMenuConfig.migrateFrom(false))
    }
}
