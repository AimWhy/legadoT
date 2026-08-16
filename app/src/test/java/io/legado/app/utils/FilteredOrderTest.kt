package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FilteredOrderTest {

    private data class Item(val id: String, val value: String)

    @Test
    fun `reorders visible items without moving hidden items`() {
        val all = listOf("a", "x", "b", "y")

        val reordered = mergeFilteredOrder(all, listOf("y", "x")) { it }

        assertEquals(listOf("a", "y", "b", "x"), reordered)
    }

    @Test
    fun `ignores stale and duplicate ordered items`() {
        val all = listOf("a", "b", "c")

        val reordered = mergeFilteredOrder(all, listOf("missing", "c", "c", "a")) { it }

        assertEquals(listOf("c", "b", "a"), reordered)
    }

    @Test
    fun `uses current items instead of stale ordered copies`() {
        val all = listOf(Item("a", "latest a"), Item("b", "latest b"))
        val staleOrder = listOf(Item("b", "stale b"), Item("a", "stale a"))

        val reordered = mergeFilteredOrder(all, staleOrder) { it.id }

        assertEquals(listOf("latest b", "latest a"), reordered.map { it.value })
    }
}
