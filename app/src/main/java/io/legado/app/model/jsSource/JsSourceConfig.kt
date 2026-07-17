package io.legado.app.model.jsSource

import com.google.gson.JsonObject
import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.GSON
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Function as JsFunction
import kotlin.coroutines.CoroutineContext

/**
 * 纯JS单文件源的 config 提取与校验(spec §5)。
 * 无能力 scope:仅标准库原型,不注入 java/source/cookie/cache 也不挂 CryptoJS——
 * 顶层只有声明、函数体不执行,导入预览阶段执行用户脚本因此无 IO 能力。
 * 脚本是元数据唯一真理源:保存/导入都经本提取覆盖 BookSource 元字段。
 */
object JsSourceConfig {

    val requiredFunctions = listOf("search", "getChapters", "getContent")

    /** config 不接受的键:主脚本自身与六个声明式规则字段(spec §1) */
    private val strippedKeys = listOf(
        "mainJs", "ruleSearch", "ruleExplore", "ruleBookInfo",
        "ruleToc", "ruleContent", "ruleReview",
    )

    @Throws(NoStackTraceException::class)
    fun extract(text: String, coroutineContext: CoroutineContext? = null): BookSource {
        val bindings = ScriptBindings()
        val scope = RhinoScriptEngine.getRuntimeScope(bindings)
        try {
            RhinoScriptEngine.eval(text, scope, coroutineContext)
        } catch (e: Exception) {
            throw NoStackTraceException("JS源脚本执行失败: ${e.message}")
        }
        val configRaw = ScriptableObject.getProperty(scope, "source")
        if (configRaw == null || configRaw === Scriptable.NOT_FOUND) {
            throw NoStackTraceException("JS源缺少顶层 source 配置对象")
        }
        val json = JsSourceEngine.normalizeJsResult(configRaw, coroutineContext)
            ?: throw NoStackTraceException("source 配置对象无法解析")
        val jsonObj = runCatching { GSON.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: throw NoStackTraceException("source 配置对象不是合法对象")
        strippedKeys.forEach { jsonObj.remove(it) }
        val bookSource = runCatching { GSON.fromJson(jsonObj, BookSource::class.java) }.getOrNull()
            ?: throw NoStackTraceException("source 配置对象字段类型不符")
        if (bookSource.bookSourceUrl.isNullOrBlank()) {
            throw NoStackTraceException("JS源 source.bookSourceUrl 不能为空")
        }
        if (bookSource.bookSourceName.isNullOrBlank()) {
            throw NoStackTraceException("JS源 source.bookSourceName 不能为空")
        }
        requiredFunctions.forEach { name ->
            if (ScriptableObject.getProperty(scope, name) !is JsFunction) {
                throw NoStackTraceException("JS源缺少必备函数 $name")
            }
        }
        if (!bookSource.exploreUrl.isNullOrBlank() &&
            ScriptableObject.getProperty(scope, "explore") !is JsFunction
        ) {
            throw NoStackTraceException("JS源声明了 exploreUrl,缺少配对的 explore 函数")
        }
        bookSource.mainJs = text
        return bookSource
    }

    /** 数字字面量或 Date.now() 形态的 lastUpdateTime 声明(键可带引号),只认首个 */
    private val lastUpdateTimeRegex =
        Regex("""(["']?lastUpdateTime["']?\s*:\s*)(Date\.now\(\)|\d+)""")

    /**
     * 把脚本文本中声明的 lastUpdateTime 改写为 [stamp],供编辑器保存时把新版本号写回
     * 脚本——脚本文本与库内列保持单一时钟,分享出去的文件才带得上新版本号。
     * 声明缺失或是其他表达式形态时返回 null,原文不动。
     */
    fun stampLastUpdateTime(text: String, stamp: Long): String? {
        val match = lastUpdateTimeRegex.find(text) ?: return null
        return text.replaceRange(match.range, "${match.groupValues[1]}$stamp")
    }
}
