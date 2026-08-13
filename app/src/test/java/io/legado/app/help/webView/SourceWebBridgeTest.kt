package io.legado.app.help.webView

import org.junit.Assert.assertTrue
import org.junit.Test

class SourceWebBridgeTest {

    @Test
    fun documentStartScriptExposesTopLevelBridgeAndAsyncApi() {
        val script = SourceWebBridge.documentStartScript("nativeJava", "nativeSource", "nativeCache")

        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("window.java = nativeJava"))
        assertTrue(script.contains("window.source = nativeSource"))
        assertTrue(script.contains("window.cache = nativeCache"))
        assertTrue(script.contains("'ajax'"))
        assertTrue(script.contains("'webViewGetSource'"))
        assertTrue(script.contains("window[`") && script.contains("Await`]"))
        assertTrue(script.contains("window.__legadoBridgeResult"))
    }
}
