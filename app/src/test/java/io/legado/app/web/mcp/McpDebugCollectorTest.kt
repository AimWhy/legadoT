package io.legado.app.web.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpDebugCollectorTest {

    @Test
    fun accumulateAndFinishOn1000() = runBlocking {
        val c = McpDebugCollector()
        c.printLog(1, "step1")
        c.printLog(10, "raw html ignored")
        c.printLog(1000, "︽解析完成")
        assertTrue(c.awaitFinished(1000))
        assertEquals("step1\n︽解析完成\n", c.snapshot())
    }

    @Test
    fun finishOnErrorState() = runBlocking {
        val c = McpDebugCollector()
        c.printLog(-1, "boom")
        assertTrue(c.awaitFinished(1000))
        assertTrue(c.snapshot().contains("boom"))
    }

    @Test
    fun timeoutReturnsPartial() = runBlocking {
        val c = McpDebugCollector()
        c.printLog(1, "partial")
        assertFalse(c.awaitFinished(50))
        assertEquals("partial\n", c.snapshot())
    }

    @Test
    fun onLineReceivesPrintedLinesOnly() = runBlocking {
        val received = mutableListOf<String>()
        val c = McpDebugCollector(onLine = { received.add(it) })
        c.printLog(1, "step1")
        c.printLog(10, "raw html ignored")
        c.printLog(1000, "done")
        assertTrue(c.awaitFinished(1000))
        assertEquals(listOf("step1", "done"), received)
    }
}
