package io.legado.app.web.mcp

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class KtorBindProbeTest {
    @Test
    fun startWaitFalseThrowsOnPortConflict() {
        val sock = ServerSocket(0)
        val port = sock.localPort
        var threw = false
        try {
            val engine = embeddedServer(CIO, port = port, host = "127.0.0.1") {}
            try {
                engine.start(wait = false)
                Thread.sleep(300)
            } catch (e: Exception) {
                threw = true
            } finally {
                runCatching { engine.stop(100, 200) }
            }
        } finally {
            sock.close()
        }
        assertTrue("start(wait=false) 应在端口占用时同步抛出", threw)
    }
}
