package io.legado.app.web.mcp

import io.legado.app.data.entities.BookSource
import io.legado.app.model.Debug
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 以 Debug.Callback 收集一次书源调试的全部日志。
 * state -1/1000 是 Debug 的结束信号;10/20/30/40 是原始 HTML 帧,不入文本。
 * Debug 是全局单例:收集期间独占 callback 槽,collect 结束(含超时)后释放。
 */
class McpDebugCollector : Debug.Callback {

    private val notPrintState = arrayOf(10, 20, 30, 40)
    private val lines = StringBuilder()
    private val finished = CompletableDeferred<Unit>()

    override fun printLog(state: Int, msg: String) {
        if (state in notPrintState) return
        synchronized(lines) { lines.appendLine(msg) }
        if (state == -1 || state == 1000) {
            finished.complete(Unit)
        }
    }

    fun snapshot(): String = synchronized(lines) { lines.toString() }

    suspend fun awaitFinished(timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) { finished.await() } != null
    }

    suspend fun collect(
        scope: CoroutineScope,
        source: BookSource,
        key: String,
        timeoutMs: Long,
    ): Pair<String, Boolean> {
        try {
            Debug.callback = this
            Debug.startDebug(scope, source, key)
            val done = awaitFinished(timeoutMs)
            return snapshot() to !done
        } finally {
            Debug.cancelDebug(true)
        }
    }
}
