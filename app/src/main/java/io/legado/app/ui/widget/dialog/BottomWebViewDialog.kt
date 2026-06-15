package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.ActivityInfo
import androidx.annotation.Keep
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.databinding.DialogWebViewBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.applyCompatibilitySettings
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.invisible
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection

class BottomWebViewDialog() : BottomSheetDialogFragment(R.layout.dialog_web_view) {

    constructor(
        sourceKey: String,
        url: String,
        html: String? = null,
        preloadJs: String? = null,
        config: String? = null
    ) : this() {
        arguments = Bundle().apply {
            putString("sourceKey", sourceKey)
            putString("url", url)
            putString("html", html)
            putString("preloadJs", preloadJs)
            putString("config", config)
        }
    }

    @Keep
    data class Config(
        var state: Int? = null,
        var peekHeight: Int? = null,
        var isHideable: Boolean? = null,
        var skipCollapsed: Boolean? = null,
        var setHalfExpandedRatio: Float? = null,
        var setExpandedOffset: Int? = null,
        var setFitToContents: Boolean? = null,
        var isDraggable: Boolean? = null,
        var isDraggableOnNestedScroll: Boolean? = null,
        var significantVelocityThreshold: Int? = null,
        var hideFriction: Float? = null,
        var maxWidth: Int? = null,
        var maxHeight: Int? = null,
        var isGestureInsetBottomIgnored: Boolean? = null,
        var expandedCornersRadius: Float? = null,
        var setUpdateImportantForAccessibilityOnSiblings: Boolean? = null,
        var backgroundDimAmount: Float? = null,
        var shouldDimBackground: Boolean? = null,
        var webViewInitialScale: Int? = null,
        var webViewCacheMode: Int? = null,
        var dismissOnTouchOutside: Boolean? = null,
        var hardwareAccelerated: Boolean? = null,
        var isNestedScrollingEnabled: Boolean? = null,
        var widthPercentage: Float? = null,
        var heightPercentage: Float? = null,
        var responsiveBreakpoint: Int? = null,
        var dialogHeight: Int? = null,
        var longClickSaveImg: Boolean? = null,
        var scrollNoDraggable: Boolean? = null
    )

    private val binding by viewBinding(DialogWebViewBinding::bind)
    private val bottomSheet by lazy {
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
    }
    private val behavior by lazy {
        bottomSheet?.let { sheet -> BottomSheetBehavior.from(sheet) }
    }
    private val displayMetrics by lazy { resources.displayMetrics }
    private lateinit var currentWebView: WebView
    private var source: BaseSource? = null
    private var preloadJs: String? = null
    private var isFullScreen = false
    private var customWebViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originOrientation: Int? = null
    private var jsInjected = false
    private var currentConfig: Config? = null
    private val basicJsName = "java"

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        kotlin.runCatching {
            manager.beginTransaction().remove(this).commit()
            super.show(manager, tag)
        }.onFailure {
            AppLog.put("显示对话框失败 tag:$tag", it)
        }
    }

    private fun setConfig(config: Config, first: Boolean = false) {
        if (!isAdded || context == null) return
        behavior?.let { behavior ->
            config.state?.let { behavior.state = it }
            config.peekHeight?.let { behavior.peekHeight = it }
            config.isHideable?.let { behavior.isHideable = it }
            config.skipCollapsed?.let { behavior.skipCollapsed = it }
            config.setHalfExpandedRatio?.let { behavior.setHalfExpandedRatio(it) }
            config.setExpandedOffset?.let { behavior.setExpandedOffset(it) }
            config.setFitToContents?.let { behavior.setFitToContents(it) }
            config.isDraggable?.let { behavior.isDraggable = it }
            config.isDraggableOnNestedScroll?.let { behavior.isDraggableOnNestedScroll = it }
            config.significantVelocityThreshold?.let { behavior.significantVelocityThreshold = it }
            config.hideFriction?.let { behavior.hideFriction = it }
            config.maxWidth?.let { behavior.maxWidth = it }
            config.maxHeight?.let { behavior.maxHeight = it }
            config.isGestureInsetBottomIgnored?.let { behavior.isGestureInsetBottomIgnored = it }
            config.setUpdateImportantForAccessibilityOnSiblings?.let {
                behavior.setUpdateImportantForAccessibilityOnSiblings(it)
            }
        }

        dialog?.let { dialog ->
            config.backgroundDimAmount?.let { amount -> dialog.window?.setDimAmount(amount) }
            config.shouldDimBackground?.let { shouldDim ->
                if (!shouldDim) dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            config.dismissOnTouchOutside?.let { touchOutside -> isCancelable = touchOutside }
            config.hardwareAccelerated?.let { hwAccel ->
                if (hwAccel) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            }
        }

        currentWebView.let { webView ->
            config.webViewInitialScale?.let { scale ->
                webView.settings.apply {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    textZoom = scale
                }
            }
            config.webViewCacheMode?.let { cacheMode -> webView.settings.cacheMode = cacheMode }
            config.isNestedScrollingEnabled?.let { enabled -> webView.isNestedScrollingEnabled = enabled }
        }

        bottomSheet?.let { sheet ->
            val params = sheet.layoutParams
            var hasChanged = false
            config.widthPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    val width = (displayMetrics.widthPixels * percentage).toInt()
                    params.width = width
                    hasChanged = true
                }
            }
            config.heightPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    params.height = (displayMetrics.heightPixels * percentage).toInt()
                    hasChanged = true
                }
            }
            val dialogHeight = config.dialogHeight ?: if (first) ViewGroup.LayoutParams.MATCH_PARENT else null
            dialogHeight?.let { height ->
                params.height = height
                hasChanged = true
            }
            if (hasChanged) sheet.layoutParams = params
        }

        config.expandedCornersRadius?.let { radius ->
            val radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radius, displayMetrics)
            bottomSheet?.let { sheet ->
                if (radiusPx > 0f) {
                    sheet.backgroundTintList = null
                    val shapeDrawable = GradientDrawable().apply {
                        cornerRadii = floatArrayOf(
                            radiusPx, radiusPx,
                            radiusPx, radiusPx,
                            0f, 0f,
                            0f, 0f
                        )
                    }
                    sheet.background = shapeDrawable
                    sheet.clipToOutline = true
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        currentWebView.outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                            }
                        }
                        currentWebView.clipToOutline = true
                        binding.customWebView.outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                            }
                        }
                        binding.customWebView.clipToOutline = true
                    }
                } else {
                    sheet.backgroundTintList = null
                    sheet.background = null
                    sheet.clipToOutline = false
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        currentWebView.outlineProvider = null
                        currentWebView.clipToOutline = false
                        binding.customWebView.outlineProvider = null
                        binding.customWebView.clipToOutline = false
                    }
                }
            }
        }

        val scrollNoDraggable = config.scrollNoDraggable ?: if (first) true else null
        scrollNoDraggable?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (it) {
                    currentWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        behavior?.isDraggable = scrollY == 0
                    }
                } else {
                    currentWebView.setOnScrollChangeListener(null)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(0)
        currentWebView = WebView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        binding.webViewContainer.addView(currentWebView)
        lifecycleScope.launch(IO) {
            val args = arguments ?: run { dismiss(); return@launch }
            val sourceKey = args.getString("sourceKey") ?: return@launch
            val url = args.getString("url") ?: return@launch
            kotlin.runCatching {
                appDb.bookSourceDao.getBookSource(sourceKey).let {
                    if (it == null) {
                        activity?.toastOnUi("no find bookSource")
                        dismiss(); return@launch
                    }
                    source = it
                }
                args.getString("config")?.let { json ->
                    try {
                        GSON.fromJsonObject<Config>(json).getOrThrow().let { config ->
                            currentConfig = config
                            activity?.runOnUiThread { setConfig(config, true) }
                        }
                    } catch (e: Exception) {
                        AppLog.put("config err", e)
                    }
                } ?: run {
                    activity?.runOnUiThread {
                        bottomSheet?.let { sheet ->
                            val layoutParams = sheet.layoutParams
                            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                            sheet.layoutParams = layoutParams
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            currentWebView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                                behavior?.isDraggable = scrollY == 0
                            }
                        }
                    }
                }
                val analyzeUrl = AnalyzeUrl(url, source = source, coroutineContext = coroutineContext)
                val html = args.getString("html") ?: analyzeUrl.getStrResponseAwait().body
                if (html.isNullOrEmpty()) throw NoStackTraceException("html is NullOrEmpty")
                preloadJs = args.getString("preloadJs")
                val spliceHtml = if (preloadJs.isNullOrEmpty()) {
                    html
                } else {
                    val headIndex = html.indexOf("<head", ignoreCase = true)
                    if (headIndex >= 0) {
                        val closingHeadIndex = html.indexOf('>', startIndex = headIndex)
                        if (closingHeadIndex >= 0) {
                            StringBuilder(html).insert(
                                closingHeadIndex + 1,
                                "<script>\n${preloadJs}\n</script>"
                            ).toString()
                        } else {
                            html
                        }
                    } else {
                        "<script>\n${preloadJs}\n</script>" + html
                    }
                }
                currentWebView.post {
                    currentWebView.onResume()
                    initWebView(analyzeUrl.url, spliceHtml, analyzeUrl.headerMap)
                    currentWebView.clearHistory()
                }
            }.onFailure {
                currentWebView.post {
                    currentWebView.webChromeClient = CustomWebChromeClient()
                    currentWebView.webViewClient = CustomWebViewClient()
                    currentWebView.onResume()
                    currentWebView.loadDataWithBaseURL(
                        url,
                        it.stackTraceToString(),
                        "text/html",
                        "utf-8",
                        url
                    )
                    currentWebView.clearHistory()
                }
            }
        }
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.customWebView.childCount > 0) {
                    customWebViewCallback?.onCustomViewHidden()
                    return@setOnKeyListener true
                }
                if (currentWebView.canGoBack()) {
                    currentWebView.goBack()
                } else {
                    dismiss()
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun initWebView(url: String, html: String, headerMap: Map<String, String>) {
        currentWebView.webChromeClient = CustomWebChromeClient()
        currentWebView.webViewClient = CustomWebViewClient()
        currentWebView.addJavascriptInterface(JSInterface(this, source), basicJsName)
        currentWebView.applyCompatibilitySettings(url, headerMap)
        currentWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = headerMap[AppConst.UA_NAME] ?: userAgentString
        }
        currentWebView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
    }

    inner class CustomWebChromeClient : WebChromeClient() {
        override fun getDefaultVideoPoster(): Bitmap {
            return super.getDefaultVideoPoster() ?: createBitmap(100, 100)
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
            originOrientation = activity?.requestedOrientation
            isFullScreen = true
            binding.webViewContainer.invisible()
            binding.customWebView.addView(view)
            customWebViewCallback = callback
            dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }

        override fun onHideCustomView() {
            originOrientation?.let {
                activity?.requestedOrientation = it
                originOrientation = null
            }
            isFullScreen = false
            binding.webViewContainer.visible()
            binding.customWebView.removeAllViews()
            customWebViewCallback = null
            dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onCloseWindow(window: WebView?) {
            dismiss()
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (!AppConfig.recordLog) return false
            val source = source ?: return false
            AppLog.put(
                "${source.getTag()}${consoleMessage.messageLevel().name}: ${consoleMessage.message()}",
                NoStackTraceException("\n${consoleMessage.message()}\n- Line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
            )
            return true
        }
    }

    inner class CustomWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            if (request?.isForMainFrame == true) {
                AppLog.put(
                    "bottomWebView onReceivedError: ${error?.description}",
                    NoStackTraceException("url: ${request.url}")
                )
                currentWebView.loadUrl("about:blank")
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            request?.let { return shouldOverrideUrlLoading(it.url) }
            return false
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            url?.let { return shouldOverrideUrlLoading(android.net.Uri.parse(it)) }
            return false
        }

        private fun shouldOverrideUrlLoading(url: android.net.Uri): Boolean {
            return when (url.scheme) {
                "http", "https" -> false
                else -> true
            }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
            handler?.proceed()
        }
    }

    override fun onDestroyView() {
        customWebViewCallback?.onCustomViewHidden()
        originOrientation?.let { activity?.requestedOrientation = it }
        kotlin.runCatching { currentWebView.destroy() }
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val displayMetrics = resources.displayMetrics
        bottomSheet?.let { sheet ->
            currentConfig?.widthPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    val params = sheet.layoutParams
                    params.width = (displayMetrics.widthPixels * percentage).toInt()
                    sheet.layoutParams = params
                }
            }
            currentConfig?.heightPercentage?.let { percentage ->
                if (percentage in 0.0..1.0) {
                    val params = sheet.layoutParams
                    params.height = (displayMetrics.heightPixels * percentage).toInt()
                    sheet.layoutParams = params
                }
            }
        }
    }

    @Suppress("unused")
    private class JSInterface(dialog: BottomWebViewDialog, private val source: BaseSource?) {
        private val dialogRef: WeakReference<BottomWebViewDialog> = WeakReference(dialog)

        @JavascriptInterface
        fun ajax(url: String): String? {
            val s = source ?: return null
            return kotlin.runCatching {
                AnalyzeUrl(url, source = s).getStrResponse().body ?: ""
            }.getOrElse { "" }
        }

        @JavascriptInterface
        fun log(msg: String) {
            AppLog.put("bottomWebView: $msg")
        }

        @JavascriptInterface
        fun toast(msg: String) {
            appCtx.toastOnUi(msg)
        }

        @JavascriptInterface
        fun longToast(msg: String) {
            appCtx.longToastOnUi(msg)
        }

        @JavascriptInterface
        fun lockOrientation(orientation: String) {
            val fra = dialogRef.get() ?: return
            val ctx = fra.requireActivity()
            if (fra.isFullScreen && fra.dialog?.isShowing == true) {
                fra.lifecycleScope.launch(Dispatchers.Main) {
                    ctx.requestedOrientation = when (orientation) {
                        "portrait", "portrait-primary" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "portrait-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        "landscape-primary" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        "landscape-secondary" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        "any", "unspecified" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        @JavascriptInterface
        fun onCloseRequested() {
            val fra = dialogRef.get() ?: return
            if (fra.dialog?.isShowing == true) {
                fra.lifecycleScope.launch(Dispatchers.Main) {
                    fra.dismiss()
                }
            }
        }
    }
}
