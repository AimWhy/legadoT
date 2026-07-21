package io.legado.app.web.mcp

import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.HttpLogController
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.Debug
import io.legado.app.model.HttpRecord
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

/**
 * MCP 8 工具注册:直调 app 内部(controller/DAO/Debug),不经 HTTP 回环,
 * Web 服务关闭时全功能可用。description/返回文本与原 Node 薄代理一致。
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
                ),
            ),
        )
        registerTools(server)
        return server
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
                    val (log, timedOut) = McpDebugCollector()
                        .collect(debugScope, source, key, timeoutSec * 1000L)
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
    }
}
