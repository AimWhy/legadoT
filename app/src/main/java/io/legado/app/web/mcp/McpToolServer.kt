package io.legado.app.web.mcp

import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.HttpLogController
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.Debug
import io.legado.app.model.HttpRecord
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.LoggingLevel
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotification
import io.modelcontextprotocol.kotlin.sdk.types.LoggingMessageNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import splitties.init.appCtx
import java.time.Instant

/**
 * MCP 12 工具注册:直调 app 内部(controller/DAO/Debug),不经 HTTP 回环,
 * Web 服务关闭时全功能可用。description/返回文本与原 Node 薄代理一致。
 * 另将 assets 帮助文档(web/help/md)全量注册为 resources。
 * Debug 是全局单例:debugMutex 串行化 MCP 侧调试,他端(调试页/校验)占用时直接报忙。
 */
object McpToolServer {

    private val debugScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debugMutex = Mutex()

    fun create(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "legado",
                version = AppConst.appInfo.versionName,
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                    resources = ServerCapabilities.Resources(),
                    logging = buildJsonObject {},
                ),
            ),
        )
        registerTools(server)
        registerResources(server)
        return server
    }

    private val helpDescriptions = mapOf(
        "jsHelp" to "书源 JS 扩展 API(java.* 方法)文档",
        "ruleHelp" to "书源规则语法(CSS/XPath/JSONPath/正则/JS)总览",
        "xpathHelp" to "XPath 规则语法",
        "regexHelp" to "正则规则语法",
        "debugHelp" to "书源调试用法与调试入口格式",
        "SourceMBookHelp" to "书源制作教程",
        "SourceMRssHelp" to "RSS 订阅源制作教程",
    )

    private fun registerResources(server: Server) {
        val names = appCtx.assets.list("web/help/md").orEmpty()
            .filter { it.endsWith(".md") }
            .map { it.removeSuffix(".md") }
        for (name in names) {
            val uri = "legado://help/$name"
            server.addResource(
                uri = uri,
                name = name,
                description = helpDescriptions[name] ?: "应用内帮助文档:$name",
                mimeType = "text/markdown",
            ) { _ ->
                val text = String(appCtx.assets.open("web/help/md/$name.md").readBytes())
                ReadResourceResult(
                    contents = listOf(
                        TextResourceContents(text = text, uri = uri, mimeType = "text/markdown")
                    )
                )
            }
        }
    }

    private fun ok(text: String) = CallToolResult(content = listOf(TextContent(text)))

    private fun err(text: String) =
        CallToolResult(content = listOf(TextContent(text)), isError = true)

    private fun ReturnData.dataOrThrow(): Any? {
        if (!isSuccess) throw RuntimeException(errorMsg)
        return data
    }

    private fun JsonObject?.str(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject?.int(key: String): Int? =
        this?.get(key)?.jsonPrimitive?.intOrNull

    private fun JsonObject?.bool(key: String): Boolean? =
        this?.get(key)?.jsonPrimitive?.booleanOrNull

    private fun stringProp(desc: String) = buildJsonObject {
        put("type", "string")
        put("description", desc)
    }

    private fun progressTokenOf(request: CallToolRequest): RequestId? {
        val prim = request.meta?.json?.get("progressToken") as? JsonPrimitive ?: return null
        return if (prim.isString) {
            RequestId.StringId(prim.content)
        } else {
            prim.content.toLongOrNull()?.let { RequestId.NumberId(it) }
        }
    }

    // 通知是纯增强:单条 2s 上限,失败静默,不影响工具主流程
    private suspend fun ClientConnection.sendProgressLine(
        line: String,
        progress: Int,
        total: Int?,
        progressToken: RequestId?,
        logger: String,
    ) {
        withTimeoutOrNull(2000) {
            runCatching {
                sendLoggingMessage(
                    LoggingMessageNotification(
                        LoggingMessageNotificationParams(
                            level = LoggingLevel.Info,
                            data = JsonPrimitive(line),
                            logger = logger,
                        )
                    )
                )
                if (progressToken != null) {
                    notification(
                        ProgressNotification(
                            ProgressNotificationParams(
                                progressToken = progressToken,
                                progress = progress.toDouble(),
                                total = total?.toDouble(),
                                message = line,
                            )
                        )
                    )
                }
            }
        }
    }

    private fun registerTools(server: Server) {
        server.addTool(
            name = "save_source",
            description = "推送单个书源到运行中的阅读T。纯JS单文件源发脚本原文(App 侧校验必备函数并提取元数据,报错原样返回);" +
                "声明式源发 BookSource JSON 对象。同 bookSourceUrl 重推即覆盖,App 内的分组/启用等用户态字段保留。" +
                "返回 bookSourceUrl,即 debug_source 的 url 参数。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("source", stringProp("源文本:JS 脚本原文或 BookSource JSON 对象"))
                    put("format", stringProp("js|json,缺省自动识别:首个非空白字符为 { 或 [ 判为 json,否则 js"))
                },
                required = listOf("source"),
            ),
        ) { request ->
            try {
                val source = request.arguments.str("source")
                    ?: return@addTool err("参数source不能为空")
                val fmt = request.arguments.str("format") ?: McpFormat.detectFormat(source)
                if (fmt != "js" && fmt != "json") {
                    return@addTool err("参数format必须为 js 或 json")
                }
                if (fmt == "js") {
                    val saved = BookSourceController.saveJsSource(source).dataOrThrow() as BookSource
                    ok("已保存(js):${saved.bookSourceName}\nbookSourceUrl: ${saved.bookSourceUrl}")
                } else {
                    BookSourceController.saveSource(source).dataOrThrow()
                    val parsed = GSON.fromJsonObject<Map<String, Any>>(source).getOrNull()
                    val name = parsed?.get("bookSourceName")?.toString() ?: ""
                    val url = parsed?.get("bookSourceUrl")?.toString() ?: ""
                    if (url.isNotEmpty()) {
                        ok("已保存(json):$name\nbookSourceUrl: $url")
                    } else {
                        ok("已保存(json),但无法从源文本解析出 bookSourceUrl(保存本身已成功)")
                    }
                }
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "debug_source",
            description = "在阅读T内真实运行书源调试管线,返回逐步日志(含每步请求与提取结果)。key 决定入口:" +
                "普通关键词=搜索→详情→目录→正文全管线;绝对URL=从详情起步;::URL=发现页;++URL=仅目录;--URL=仅正文。" +
                "日志过程中的请求细节可再用 get_http_logs 深挖。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("url", stringProp("书源 bookSourceUrl(save_source 的返回值)"))
                    put("key", stringProp("调试入口:关键词 / 绝对URL / ::URL / ++URL / --URL"))
                    putJsonObject("timeoutSec") {
                        put("type", "integer")
                        put("description", "超时秒数,默认 120")
                    }
                },
                required = listOf("url", "key"),
            ),
        ) { request ->
            try {
                val url = request.arguments.str("url")
                    ?: return@addTool err("参数url不能为空")
                val key = request.arguments.str("key")
                    ?: return@addTool err("参数key不能为空")
                val timeoutSec = (request.arguments.int("timeoutSec") ?: 120).coerceIn(10, 600)
                val source = appDb.bookSourceDao.getBookSource(url)
                    ?: return@addTool err("未找到源，请检查书源地址")
                if (!debugMutex.tryLock()) {
                    return@addTool err("调试通道占用中,稍后重试")
                }
                try {
                    if (Debug.callback != null || Debug.isChecking) {
                        return@addTool err("调试通道占用中,稍后重试")
                    }
                    val progressToken = progressTokenOf(request)
                    val (log, timedOut) = coroutineScope {
                        val lineChannel = Channel<String>(Channel.UNLIMITED)
                        launch {
                            var lineNo = 0
                            for (line in lineChannel) {
                                lineNo++
                                sendProgressLine(
                                    line, lineNo, null, progressToken, "legado.debug_source"
                                )
                            }
                        }
                        try {
                            McpDebugCollector(onLine = { lineChannel.trySend(it) })
                                .collect(debugScope, source, key, timeoutSec * 1000L)
                        } finally {
                            lineChannel.close()
                        }
                    }
                    val body = log.ifEmpty { "(调试无输出)" }
                    ok(
                        if (timedOut) {
                            "$body\n\n[调试超时 ${timeoutSec}s,以上为已收到的部分日志]"
                        } else {
                            body
                        }
                    )
                } finally {
                    debugMutex.unlock()
                }
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "list_sources",
            description = "列出阅读T内的书源(摘要:名称/url/分组/启用/是否JS源)。可按名称或 url 子串过滤。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("search", stringProp("名称/url 子串过滤,大小写不敏感"))
                },
                required = emptyList(),
            ),
        ) { request ->
            try {
                val search = request.arguments.str("search")
                val all = appDb.bookSourceDao.all
                if (all.isEmpty()) {
                    ok("(App 内无书源)")
                } else {
                    val summaries = McpFormat.summarizeSources(all, search)
                    ok("共 ${summaries.size} 条\n" + McpFormat.truncate(McpFormat.toPrettyJson(summaries)))
                }
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "get_source",
            description = "按 bookSourceUrl 取完整书源 JSON(JS 源脚本全文在 mainJs 字段)。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("url", stringProp("书源 bookSourceUrl"))
                },
                required = listOf("url"),
            ),
        ) { request ->
            try {
                val url = request.arguments.str("url")
                    ?: return@addTool err("参数url不能为空")
                val bs = appDb.bookSourceDao.getBookSource(url)
                    ?: return@addTool err("未找到源，请检查书源地址")
                ok(McpFormat.truncate(McpFormat.prettyJson(GSON.toJson(bs)), 200_000))
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "delete_sources",
            description = "按 bookSourceUrl 删除阅读T内的书源(可多个)。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("urls") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                        put("description", "bookSourceUrl 列表")
                    }
                },
                required = listOf("urls"),
            ),
        ) { request ->
            try {
                val urls = request.arguments?.get("urls")?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                if (urls.isEmpty()) {
                    return@addTool err("参数urls不能为空")
                }
                SourceHelp.deleteBookSources(urls.map { BookSource(bookSourceUrl = it) })
                ok("已删除 ${urls.size} 个书源")
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "get_http_logs",
            description = "拉取阅读T端 HTTP 请求日志摘要(最新在前,内存上限 50 条)。调试书源失败时用它定位 App 实发请求,再用 get_http_log 看详情。" +
                "需在 App 设置开启「记录HTTP日志」,未开启时会明确提示。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "条数,默认 50")
                    }
                },
                required = emptyList(),
            ),
        ) { request ->
            try {
                val limit = request.arguments.int("limit") ?: 50
                val data = HttpLogController.getLogs(mapOf("limit" to listOf(limit.toString())))
                    .dataOrThrow() as Map<*, *>
                val recording = data["recording"] as Boolean
                val logs = data["logs"] as List<*>
                val lines = logs.map { item ->
                    val m = item as Map<*, *>
                    "#${m["id"]} ${Instant.ofEpochMilli(m["time"] as Long)} ${m["method"]} ${m["url"]}" +
                        " → ${m["statusCode"]} ${m["duration"]}ms" +
                        (m["error"]?.let { " | $it" } ?: "")
                }
                val head = if (recording) {
                    "最新 ${lines.size} 条(内存上限 50):"
                } else {
                    "「记录HTTP日志」开关未开启(可用 set_http_log_recording 开启),以下为开关关闭前的残留记录:"
                }
                ok(head + "\n" + if (lines.isNotEmpty()) lines.joinToString("\n") else "(空)")
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "get_http_log",
            description = "按 id 取单条 HTTP 请求完整记录(请求/响应头+体)。正文在 App 记录时已截断至 4096 字符,完整响应体以 PC 侧采集存档为准;" +
                "此记录的价值是看 App 实发请求头(Cookie/UA)与响应差异。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("id") {
                        put("type", "integer")
                        put("description", "get_http_logs 返回的记录 id")
                    }
                },
                required = listOf("id"),
            ),
        ) { request ->
            try {
                val id = request.arguments.int("id")
                    ?: return@addTool err("参数id不能为空")
                val r = HttpLogController.getLog(mapOf("id" to listOf(id.toString())))
                    .dataOrThrow() as HttpRecord
                val parts = mutableListOf(
                    "#${r.id} ${r.method} ${r.url}",
                    "status: ${r.statusCode}  duration: ${r.duration}ms  time: ${Instant.ofEpochMilli(r.time)}",
                    "",
                    "-- 请求头 --",
                    r.requestHeaders,
                )
                if (r.requestBody.isNotEmpty()) {
                    parts += listOf("", "-- 请求体 --", McpFormat.truncate(r.requestBody))
                }
                parts += listOf("", "-- 响应头 --", r.responseHeaders)
                if (r.responseBody.isNotEmpty()) {
                    parts += listOf("", "-- 响应体 --", McpFormat.truncate(r.responseBody))
                }
                if (!r.error.isNullOrEmpty()) {
                    parts += listOf("", "-- 错误 --", r.error)
                }
                ok(parts.joinToString("\n"))
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "set_http_log_recording",
            description = "远程开关阅读T的「记录HTTP日志」,与 App 设置页开关同步。开启后 App 发出的请求才会被记录(get_http_logs 可查);" +
                "调试深挖前开启,收尾时关闭。状态持久在 App 设置里,非会话态;切换不清空已有记录。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    putJsonObject("enabled") {
                        put("type", "boolean")
                        put("description", "true 开启记录,false 关闭")
                    }
                },
                required = listOf("enabled"),
            ),
        ) { request ->
            try {
                val enabled = request.arguments.bool("enabled")
                    ?: return@addTool err("参数enabled必须为布尔值")
                HttpLogController.setRecording("""{"enabled":$enabled}""").dataOrThrow()
                ok("「记录HTTP日志」已${if (enabled) "开启" else "关闭"}")
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "eval_js",
            description = "在阅读T内真实的书源 JS 环境执行一段脚本,返回求值结果与脚本内 java.log 的输出。" +
                "运行时绑定与书源一致:java/source/cookie/cache/baseUrl + CryptoJS 共享库。" +
                "传 url 则绑定库内真源(java.ajax 按该源的 UA/Cookie/并发率发请求);" +
                "不传则用空白源裸跑,适合纯引擎行为/加密算法探针。与 debug_source 共用调试通道,占用时报忙。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("js", stringProp("要执行的 JS 脚本文本"))
                    put("url", stringProp("书源 bookSourceUrl,缺省用空白源裸跑"))
                    putJsonObject("timeoutSec") {
                        put("type", "integer")
                        put("description", "超时秒数,默认 60")
                    }
                },
                required = listOf("js"),
            ),
        ) { request ->
            try {
                val js = request.arguments.str("js")
                    ?: return@addTool err("参数js不能为空")
                val url = request.arguments.str("url")
                val timeoutSec = (request.arguments.int("timeoutSec") ?: 60).coerceIn(5, 600)
                val source = if (url.isNullOrEmpty()) {
                    BookSource()
                } else {
                    appDb.bookSourceDao.getBookSource(url)
                        ?: return@addTool err("未找到源，请检查书源地址")
                }
                if (!debugMutex.tryLock()) {
                    return@addTool err("调试通道占用中,稍后重试")
                }
                try {
                    if (Debug.callback != null || Debug.isChecking) {
                        return@addTool err("调试通道占用中,稍后重试")
                    }
                    val collector = McpDebugCollector()
                    try {
                        // cancelDebug(true) 摘的是全局 callback 槽,挂载后才可执行,busy 早退走外层只解锁
                        Debug.callback = collector
                        Debug.startSimpleDebug(source.getKey())
                        val startMs = System.currentTimeMillis()
                        val deferred = debugScope.async {
                            val evalContext = currentCoroutineContext()
                            runCatching { source.evalJS(js, evalContext) }
                        }
                        val outcome = withTimeoutOrNull(timeoutSec * 1000L) { deferred.await() }
                        val elapsedMs = System.currentTimeMillis() - startMs
                        if (outcome == null) {
                            deferred.cancel()
                        }
                        val logs = collector.snapshot().trimEnd()
                        val logSection = if (logs.isEmpty()) "" else "-- 日志 --\n$logs\n\n"
                        when {
                            outcome == null -> ok(
                                McpFormat.truncate(
                                    logSection + "[求值超时 ${timeoutSec}s,已发起取消,脚本将在下个检查点中止]"
                                )
                            )
                            outcome.isSuccess -> ok(
                                McpFormat.truncate(
                                    logSection + "-- 结果 --\n" +
                                        McpFormat.renderEvalResult(outcome.getOrNull()) +
                                        "\n\n耗时 ${elapsedMs}ms"
                                )
                            )
                            else -> {
                                val e = outcome.exceptionOrNull()!!
                                err(
                                    McpFormat.truncate(
                                        logSection + "-- 错误 --\n" + (e.localizedMessage ?: e.toString())
                                    )
                                )
                            }
                        }
                    } finally {
                        Debug.cancelDebug(true)
                    }
                } finally {
                    debugMutex.unlock()
                }
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "get_cookies",
            description = "读取阅读T内某域的 cookie(持久层+会话层合并,与书源 JS 的 cookie 对象同源)。按 url 的二级域名取。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("url", stringProp("URL 或域名,按其二级域名读取"))
                },
                required = listOf("url"),
            ),
        ) { request ->
            try {
                val url = request.arguments.str("url")
                    ?: return@addTool err("参数url不能为空")
                val cookie = CookieStore.getCookie(url)
                ok(if (cookie.isEmpty()) "(该域无 cookie)" else McpFormat.truncate(cookie))
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "set_cookie",
            description = "向阅读T写入某域的 cookie,按键合并、不抹掉该域已有的其它键。" +
                "典型用法:PC 侧浏览器完成登录后把登录态推进 App,再用 debug_source 调试需登录的源。" +
                "格式:key1=value1; key2=value2",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("url", stringProp("URL 或域名,写入其二级域名"))
                    put("cookie", stringProp("cookie 字符串,分号分隔键值对"))
                },
                required = listOf("url", "cookie"),
            ),
        ) { request ->
            try {
                val url = request.arguments.str("url")
                    ?: return@addTool err("参数url不能为空")
                val cookie = request.arguments.str("cookie")
                    ?: return@addTool err("参数cookie不能为空")
                CookieStore.replaceCookie(url, cookie)
                ok("已写入,该域当前 cookie:\n" + McpFormat.truncate(CookieStore.getCookie(url)))
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }

        server.addTool(
            name = "clear_cookies",
            description = "清除阅读T内某域的全部 cookie(持久层+会话层+WebView 一并清)。",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("url", stringProp("URL 或域名,清除其二级域名"))
                },
                required = listOf("url"),
            ),
        ) { request ->
            try {
                val url = request.arguments.str("url")
                    ?: return@addTool err("参数url不能为空")
                CookieStore.removeCookie(url)
                ok("已清除该域 cookie")
            } catch (e: Exception) {
                err(e.localizedMessage ?: e.toString())
            }
        }
    }
}
