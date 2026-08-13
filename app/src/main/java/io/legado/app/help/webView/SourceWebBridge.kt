package io.legado.app.help.webView

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.script.rhino.runScriptWithContext
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.source.getSourceType
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.jsSource.JsSourceEngine
import io.legado.app.ui.association.AddToBookshelfDialog
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.WeakHashMap
import kotlin.coroutines.EmptyCoroutineContext

/** Fixed WebView bridge compatible with legado-E/main@21855a7b. */
object SourceWebBridge {

    interface Callback {
        fun upConfig(config: String) = Unit
        fun lockOrientation(orientation: String) = Unit
        fun close() = Unit
    }

    private data class Installation(
        val sourceKey: String,
        val javaName: String,
        val sourceName: String,
        val cacheName: String,
        val javaBridge: JavaBridge,
        val scriptHandler: ScriptHandler?,
    )

    private val installations = WeakHashMap<WebView, Installation>()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    @Synchronized
    fun install(
        webView: WebView,
        source: BaseSource,
        activity: AppCompatActivity? = null,
        callback: Callback? = null,
    ) {
        if (installations[webView]?.sourceKey == source.getKey()) return
        uninstall(webView)
        webView.settings.javaScriptEnabled = true

        val suffix = UUID.randomUUID().toString().replace("-", "")
        val javaName = "legadoJava_$suffix"
        val sourceName = "legadoSource_$suffix"
        val cacheName = "legadoCache_$suffix"
        val javaBridge = JavaBridge(source, activity, webView, callback)
        webView.addJavascriptInterface(javaBridge, javaName)
        webView.addJavascriptInterface(SourceBridge(source), sourceName)
        webView.addJavascriptInterface(WebCacheBridge, cacheName)

        val script = documentStartScript(javaName, sourceName, cacheName)
        val scriptHandler = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        } else {
            // addJavascriptInterface itself is present before document scripts on legacy WebView.
            webView.addJavascriptInterface(JavaBridge(source, activity, webView, callback), "java")
            webView.addJavascriptInterface(SourceBridge(source), "source")
            webView.addJavascriptInterface(WebCacheBridge, "cache")
            webView.evaluateJavascript(script, null)
            null
        }
        installations[webView] = Installation(
            source.getKey(), javaName, sourceName, cacheName, javaBridge, scriptHandler
        )
    }

    @Synchronized
    fun uninstall(webView: WebView) {
        installations.remove(webView)?.let {
            it.scriptHandler?.remove()
            it.javaBridge.cancel()
            webView.removeJavascriptInterface(it.javaName)
            webView.removeJavascriptInterface(it.sourceName)
            webView.removeJavascriptInterface(it.cacheName)
            webView.removeJavascriptInterface("java")
            webView.removeJavascriptInterface("source")
            webView.removeJavascriptInterface("cache")
        }
    }

    internal fun documentStartScript(javaName: String, sourceName: String, cacheName: String): String = """
        (() => {
          if (window.top !== window) return;
          const nativeJava = window[${GSON.toJson(javaName)}];
          const nativeSource = window[${GSON.toJson(sourceName)}];
          const nativeCache = window[${GSON.toJson(cacheName)}];
          window.java = nativeJava;
          window.source = nativeSource;
          window.cache = nativeCache;
          const pending = new Map();
          const request = (name, args) => new Promise((resolve, reject) => {
            const id = `req_${'$'}{Date.now()}_${'$'}{Math.random().toString(36).slice(2)}`;
            pending.set(id, { resolve, reject });
            nativeJava.request(name, Array.from(args, value => value == null ? null : String(value)), id);
          });
          window.__legadoBridgeResult = (id, success) => {
            const callback = pending.get(id);
            if (!callback) return;
            const value = nativeCache.getFromMemory(id);
            nativeCache.deleteMemory(id);
            pending.delete(id);
            (success ? callback.resolve : callback.reject)(value);
          };
          window.run = code => request('run', [code]);
          for (const name of ['ajax', 'connect', 'get', 'head', 'post', 'webView',
            'webViewGetSource', 'decryptStr', 'encryptBase64', 'encryptHex',
            'createSignHex', 'downloadFile', 'readTxtFile', 'importScript', 'getString']) {
            window[`${'$'}{name}Await`] = (...args) => request(`${'$'}{name}Await`, args);
          }
        })();
    """.trimIndent()
}

private object WebCacheBridge {
    @JavascriptInterface
    @JvmOverloads
    fun put(key: String, value: String, saveTime: Int = 0) = CacheManager.put(key, value, saveTime)

    @JavascriptInterface
    fun putMemory(key: String, value: String) = CacheManager.putMemory(key, value)

    @JavascriptInterface
    fun getFromMemory(key: String): String? = CacheManager.getFromMemory(key)?.toString()

    @JavascriptInterface
    fun deleteMemory(key: String) = CacheManager.deleteMemory(key)

    @JavascriptInterface
    fun get(key: String): String? = CacheManager.get(key)

    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? = CacheManager.get(key, onlyDisk)

    @JavascriptInterface
    @JvmOverloads
    fun putFile(key: String, value: String, saveTime: Int = 0) =
        CacheManager.putFile(key, value, saveTime)

    @JavascriptInterface
    fun getFile(key: String): String? = CacheManager.getFile(key)

    @JavascriptInterface
    fun delete(key: String) = CacheManager.delete(key)
}

private class SourceBridge(private val source: BaseSource) {
    @JavascriptInterface
    fun login() = source.login()

    @JavascriptInterface
    fun getLoginHeader(): String? = source.getLoginHeader()

    @JavascriptInterface
    fun getLoginInfo(): String? = source.getLoginInfo()

    @JavascriptInterface
    fun putLoginInfo(info: String): Boolean = source.putLoginInfo(info)

    @JavascriptInterface
    fun removeLoginInfo() = source.removeLoginInfo()

    @JavascriptInterface
    fun putVariable(variable: String?) = source.setVariable(variable)

    @JavascriptInterface
    fun getVariable(): String = source.getVariable()

    @JavascriptInterface
    fun put(key: String, value: String): String = source.put(key, value)

    @JavascriptInterface
    fun get(key: String): String = source.get(key)
}

private class JavaBridge(
    private val source: BaseSource,
    activity: AppCompatActivity?,
    webView: WebView,
    callback: SourceWebBridge.Callback?,
) {
    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)
    private val callbackRef = callback
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analyzeRule by lazy { AnalyzeRule(source = source) }

    private fun <T> withRhino(block: () -> T): T =
        runScriptWithContext(EmptyCoroutineContext, block)

    @JavascriptInterface
    fun upConfig(config: String) {
        webViewRef.get()?.post { callbackRef?.upConfig(config) }
    }

    @JavascriptInterface
    fun lockOrientation(orientation: String) {
        webViewRef.get()?.post { callbackRef?.lockOrientation(orientation) }
    }

    @JavascriptInterface
    fun onCloseRequested() {
        webViewRef.get()?.post { callbackRef?.close() }
    }

    @JavascriptInterface
    fun toast(msg: String?) = withRhino { source.toast(msg) }

    @JavascriptInterface
    fun longToast(msg: String?) = withRhino { source.longToast(msg) }

    @JavascriptInterface
    fun log(msg: String?): String = withRhino { source.log(msg).toString() }

    @JavascriptInterface
    fun md5Encode(value: String): String = source.md5Encode(value)

    @JavascriptInterface
    fun md5Encode16(value: String): String = source.md5Encode16(value)

    @JavascriptInterface
    fun base64Decode(value: String?): String = source.base64Decode(value)

    @JavascriptInterface
    fun base64Decode(value: String?, charset: String): String = source.base64Decode(value, charset)

    @JavascriptInterface
    fun base64Decode(value: String, flags: Int): String = source.base64Decode(value, flags)

    @JavascriptInterface
    fun base64Encode(value: String): String? = source.base64Encode(value)

    @JavascriptInterface
    fun base64Encode(value: String, flags: Int): String? = source.base64Encode(value, flags)

    @JavascriptInterface
    fun hexDecodeToString(value: String): String? = source.hexDecodeToString(value)

    @JavascriptInterface
    fun hexEncodeToString(value: String): String? = source.hexEncodeToString(value)

    @JavascriptInterface
    fun timeFormatUTC(time: Long, format: String, offset: Int): String? =
        source.timeFormatUTC(time, format, offset)

    @JavascriptInterface
    fun timeFormat(time: Long): String = source.timeFormat(time)

    @JavascriptInterface
    fun encodeURI(value: String): String = source.encodeURI(value)

    @JavascriptInterface
    fun encodeURI(value: String, charset: String): String = source.encodeURI(value, charset)

    @JavascriptInterface
    fun htmlFormat(value: String): String = source.htmlFormat(value)

    @JavascriptInterface
    fun t2s(value: String): String = source.t2s(value)

    @JavascriptInterface
    fun s2t(value: String): String = source.s2t(value)

    @JavascriptInterface
    fun getWebViewUA(): String = source.getWebViewUA()

    @JavascriptInterface
    fun toNumChapter(value: String?): String? = source.toNumChapter(value)

    @JavascriptInterface
    fun randomUUID(): String = source.randomUUID()

    @JavascriptInterface
    fun androidId(): String = source.androidId()

    @JavascriptInterface
    fun getReadBookConfig(): String = GSON.toJson(ReadBookConfig.durConfig)

    @JavascriptInterface
    fun getThemeMode(): String = AppConfig.themeMode ?: "0"

    @JavascriptInterface
    fun getThemeConfig(): String = GSON.toJson(
        ThemeConfig.configList.firstOrNull { it.isNightTheme == AppConfig.isNightTheme }
    )

    @JavascriptInterface
    fun openUrl(url: String) = withRhino { source.openUrl(url) }

    @JavascriptInterface
    fun openUrl(url: String, mimeType: String?) = withRhino { source.openUrl(url, mimeType) }

    @JavascriptInterface
    fun importScript(path: String): String = withRhino { source.importScript(path) }

    @JavascriptInterface
    fun cacheFile(url: String): String = withRhino { source.cacheFile(url) }

    @JavascriptInterface
    fun cacheFile(url: String, saveTime: Int): String = withRhino { source.cacheFile(url, saveTime) }

    @JavascriptInterface
    fun getCookie(tag: String): String = source.getCookie(tag)

    @JavascriptInterface
    fun getCookie(tag: String, key: String?): String = source.getCookie(tag, key)

    @JavascriptInterface
    fun downloadFile(url: String): String = withRhino { source.downloadFile(url) }

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun downloadFile(content: String, url: String): String =
        withRhino { source.downloadFile(content, url) }

    @JavascriptInterface
    fun readTxtFile(path: String): String = source.readTxtFile(path)

    @JavascriptInterface
    fun readTxtFile(path: String, charset: String): String = source.readTxtFile(path, charset)

    @JavascriptInterface
    fun deleteFile(path: String): Boolean = source.deleteFile(path)

    @JavascriptInterface
    fun unzipFile(path: String): String = source.unzipFile(path)

    @JavascriptInterface
    fun un7zFile(path: String): String = source.un7zFile(path)

    @JavascriptInterface
    fun unrarFile(path: String): String = source.unrarFile(path)

    @JavascriptInterface
    fun unArchiveFile(path: String): String = source.unArchiveFile(path)

    @JavascriptInterface
    fun getTxtInFolder(path: String): String = source.getTxtInFolder(path)

    @JavascriptInterface
    fun getZipStringContent(url: String, path: String): String =
        withRhino { source.getZipStringContent(url, path) }

    @JavascriptInterface
    fun getZipStringContent(url: String, path: String, charset: String): String =
        withRhino { source.getZipStringContent(url, path, charset) }

    @JavascriptInterface
    fun getRarStringContent(url: String, path: String): String =
        withRhino { source.getRarStringContent(url, path) }

    @JavascriptInterface
    fun getRarStringContent(url: String, path: String, charset: String): String =
        withRhino { source.getRarStringContent(url, path, charset) }

    @JavascriptInterface
    fun get7zStringContent(url: String, path: String): String =
        withRhino { source.get7zStringContent(url, path) }

    @JavascriptInterface
    fun get7zStringContent(url: String, path: String, charset: String): String =
        withRhino { source.get7zStringContent(url, path, charset) }

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesDecodeToString(data: String, key: String, transformation: String, iv: String): String? =
        source.aesDecodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesDecodeArgsBase64Str(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.aesDecodeArgsBase64Str(data, key, mode, padding, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesBase64DecodeToString(
        data: String, key: String, transformation: String, iv: String,
    ): String? = source.aesBase64DecodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesEncodeToString(data: String, key: String, transformation: String, iv: String): String? =
        source.aesEncodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String,
    ): String? = source.aesEncodeToBase64String(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun aesEncodeArgsBase64Str(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.aesEncodeArgsBase64Str(data, key, mode, padding, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun desDecodeToString(data: String, key: String, transformation: String, iv: String): String? =
        source.desDecodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun desBase64DecodeToString(
        data: String, key: String, transformation: String, iv: String,
    ): String? = source.desBase64DecodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun desEncodeToString(data: String, key: String, transformation: String, iv: String): String? =
        source.desEncodeToString(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun desEncodeToBase64String(
        data: String, key: String, transformation: String, iv: String,
    ): String? = source.desEncodeToBase64String(data, key, transformation, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun tripleDESDecodeStr(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.tripleDESDecodeStr(data, key, mode, padding, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun tripleDESDecodeArgsBase64Str(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.tripleDESDecodeArgsBase64Str(data, key, mode, padding, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun tripleDESEncodeBase64Str(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.tripleDESEncodeBase64Str(data, key, mode, padding, iv)

    @Suppress("DEPRECATION")
    @JavascriptInterface
    fun tripleDESEncodeArgsBase64Str(
        data: String, key: String, mode: String, padding: String, iv: String,
    ): String? = source.tripleDESEncodeArgsBase64Str(data, key, mode, padding, iv)

    @JavascriptInterface
    fun digestHex(data: String, algorithm: String): String = source.digestHex(data, algorithm)

    @JavascriptInterface
    fun digestBase64Str(data: String, algorithm: String): String =
        source.digestBase64Str(data, algorithm)

    @JavascriptInterface
    fun HMacHex(data: String, algorithm: String, key: String): String =
        source.HMacHex(data, algorithm, key)

    @JavascriptInterface
    fun HMacBase64(data: String, algorithm: String, key: String): String =
        source.HMacBase64(data, algorithm, key)

    @JavascriptInterface
    @JvmOverloads
    fun ajax(url: String, callTimeout: Int = 9000): String? = withRhino { source.ajax(url) }

    @JavascriptInterface
    @JvmOverloads
    fun connect(url: String, header: String? = null, callTimeout: Int = 9000): String =
        withRhino { if (header == null) source.connect(url) else source.connect(url, header) }.toString()

    @JavascriptInterface
    @JvmOverloads
    fun get(url: String, header: String, timeout: Int = 9000): String? =
        withRhino { source.get(url, header).body() }

    @JavascriptInterface
    @JvmOverloads
    fun head(url: String, header: String, timeout: Int = 9000): String = withRhino {
        val headers = source.head(url, header).headers()
        val result = linkedMapOf<String, String>()
        headers.forEach { name, value -> result[name] = value }
        GSON.toJson(result)
    }

    @JavascriptInterface
    @JvmOverloads
    fun post(url: String, body: String, header: String, timeout: Int = 9000): String? =
        withRhino { source.post(url, body, header).body() }

    @JavascriptInterface
    fun put(key: String, value: String): String = source.put(key, value)

    @JavascriptInterface
    fun get(key: String): String = source.get(key)

    @JavascriptInterface
    @JvmOverloads
    fun searchBook(key: String, searchScope: String? = null) {
        webViewRef.get()?.post { activityRef.get()?.let { SearchActivity.start(it, key) } }
    }

    @JavascriptInterface
    fun addBook(bookUrl: String) {
        webViewRef.get()?.post {
            activityRef.get()?.showDialogFragment(AddToBookshelfDialog(bookUrl))
        }
    }

    @JavascriptInterface
    fun showPhoto(src: String) {
        webViewRef.get()?.post {
            activityRef.get()?.showDialogFragment(PhotoDialog(src, source.getKey()))
        }
    }

    @JavascriptInterface
    @JvmOverloads
    fun open(name: String, url: String? = null, title: String? = null, origin: String? = null) {
        val activity = activityRef.get() ?: return
        activity.lifecycleScope.launch(Dispatchers.IO) {
            when (name) {
                "login" -> {
                    val target = origin?.let {
                        appDb.bookSourceDao.getBookSource(it) ?: appDb.rssSourceDao.getByKey(it)
                    } ?: source
                    if (target.loginUrl.isNullOrBlank()) return@launch
                    withContext(Dispatchers.Main) {
                        activity.startActivity<SourceLoginActivity> {
                            putExtra("type", if (target is RssSource) "rssSource" else "bookSource")
                            putExtra("key", target.getKey())
                            putExtra("sourceType", target.getSourceType())
                        }
                    }
                }
                "sort" -> {
                    val target = origin?.let { appDb.rssSourceDao.getByKey(it) }
                        ?: source as? RssSource ?: return@launch
                    withContext(Dispatchers.Main) {
                        activity.startActivity<RssSortActivity> { putExtra("url", target.sourceUrl) }
                    }
                }
                "rss" -> {
                    val target = origin?.let { appDb.rssSourceDao.getByKey(it) }
                        ?: source as? RssSource ?: return@launch
                    withContext(Dispatchers.Main) {
                        activity.startActivity<ReadRssActivity> {
                            putExtra("title", title ?: target.sourceName)
                            putExtra("origin", target.sourceUrl)
                            url?.let { putExtra("link", it) }
                        }
                    }
                }
                "search" -> title?.let { withContext(Dispatchers.Main) { SearchActivity.start(activity, it) } }
                "explore" -> {
                    val target = origin?.let { appDb.bookSourceDao.getBookSource(it) }
                        ?: source as? BookSource ?: return@launch
                    withContext(Dispatchers.Main) {
                        activity.startActivity<ExploreShowActivity> {
                            putExtra("exploreName", title)
                            putExtra("sourceUrl", target.bookSourceUrl)
                            putExtra("exploreUrl", url)
                        }
                    }
                }
            }
        }
    }

    @JavascriptInterface
    @JvmOverloads
    fun setContent(content: String, baseUrl: String? = null) {
        analyzeRule.setContent(content, baseUrl)
    }

    @JavascriptInterface
    fun setBaseUrl(baseUrl: String?) {
        analyzeRule.setBaseUrl(baseUrl)
    }

    @JavascriptInterface
    fun setRedirectUrl(url: String): String? = analyzeRule.setRedirectUrl(url)?.toString()

    @JavascriptInterface
    @JvmOverloads
    fun getStringList(rule: String?, content: String? = null, isUrl: Boolean = false): String =
        GSON.toJson(analyzeRule.getStringList(rule, content, isUrl))

    @JavascriptInterface
    @JvmOverloads
    fun getString(rule: String?, content: String? = null, isUrl: Boolean = false): String =
        analyzeRule.getString(rule, content, isUrl)

    @JavascriptInterface
    fun request(name: String, params: Array<String?>, id: String) {
        requestScope.launch {
            try {
                val value = executeRequest(name, params)
                currentCoroutineContext().ensureActive()
                completeRequest(id, true, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.put("Web bridge request $name failed", e)
                completeRequest(id, false, e.localizedMessage ?: e.toString())
            }
        }
    }

    private suspend fun executeRequest(name: String, p: Array<String?>): String? {
        val context = currentCoroutineContext()
        return runScriptWithContext(context) {
        when (name) {
            "run" -> JsSourceEngine.normalizeJsResult(analyzeRule.evalJS(p[0].orEmpty()))
            "ajaxAwait" -> source.ajax(p[0].orEmpty())
            "connectAwait" -> connect(p[0].orEmpty(), p.getOrNull(1))
            "getAwait" -> get(p[0].orEmpty(), p.getOrNull(1) ?: "{}")
            "headAwait" -> head(p[0].orEmpty(), p.getOrNull(1) ?: "{}")
            "postAwait" -> post(p[0].orEmpty(), p.getOrNull(1).orEmpty(), p.getOrNull(2) ?: "{}")
            "webViewAwait" -> source.webView(p.getOrNull(0), p.getOrNull(1), p.getOrNull(2))
            "webViewGetSourceAwait" -> source.webViewGetSource(
                p.getOrNull(0), p.getOrNull(1), p.getOrNull(2), p.getOrNull(3).orEmpty()
            )
            "decryptStrAwait" -> source.createSymmetricCrypto(
                p.getOrNull(0).orEmpty(), p.getOrNull(1).orEmpty(), p.getOrNull(2)
            ).decryptStr(p.getOrNull(3).orEmpty())
            "encryptBase64Await" -> source.createSymmetricCrypto(
                p.getOrNull(0).orEmpty(), p.getOrNull(1).orEmpty(), p.getOrNull(2)
            ).encryptBase64(p.getOrNull(3).orEmpty())
            "encryptHexAwait" -> source.createSymmetricCrypto(
                p.getOrNull(0).orEmpty(), p.getOrNull(1).orEmpty(), p.getOrNull(2)
            ).encryptHex(p.getOrNull(3).orEmpty())
            "createSignHexAwait" -> source.createSign(p.getOrNull(0).orEmpty())
                .setPublicKey(p.getOrNull(1).orEmpty())
                .setPrivateKey(p.getOrNull(2).orEmpty())
                .signHex(p.getOrNull(3).orEmpty())
            "downloadFileAwait" -> source.downloadFile(p.getOrNull(0).orEmpty())
            "readTxtFileAwait" -> source.readTxtFile(p.getOrNull(0).orEmpty())
            "importScriptAwait" -> source.importScript(p.getOrNull(0).orEmpty())
            "getStringAwait" -> getString(p.getOrNull(0), p.getOrNull(1))
            else -> error("Unknown Web bridge request: $name")
        }
        }
    }

    private fun completeRequest(id: String, success: Boolean, value: Any?) {
        CacheManager.putMemory(id, value?.toString() ?: "")
        val js = "window.__legadoBridgeResult?.(${GSON.toJson(id)}, $success);"
        webViewRef.get()?.post { webViewRef.get()?.evaluateJavascript(js, null) }
    }

    fun cancel() {
        requestScope.cancel()
    }
}
