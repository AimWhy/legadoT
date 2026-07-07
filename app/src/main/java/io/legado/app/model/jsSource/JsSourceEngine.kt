package io.legado.app.model.jsSource

import androidx.collection.LruCache
import com.script.CompiledScript
import com.script.buildScriptBindings
import com.script.rhino.RhinoContext
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.CacheManager
import io.legado.app.help.JsExtensions
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.getShareScope
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import kotlinx.coroutines.Job
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.Scriptable
import org.htmlunit.corejs.javascript.ScriptableObject
import org.htmlunit.corejs.javascript.Undefined
import org.htmlunit.corejs.javascript.Wrapper
import org.htmlunit.corejs.javascript.Function as JsFunction
import kotlin.coroutines.CoroutineContext

/**
 * 纯JS单文件源执行器(spec §2)。
 * 每次调用新建 scope(并发隔离):绑定 → 挂共享原型 → eval 主脚本 → eval 调用表达式。
 * 只走 eval 通道(allowScriptRun 正确开闸;invokeMethod 无先例且闸门不开,见 spec §7-4)。
 * args 以绑定进 scope,既是函数参数也是环境绑定,与声明式源 key/page/book 惯例一致。
 */
class JsSourceEngine(
    private val source: BookSource,
    private val coroutineContext: CoroutineContext? = null,
) : JsExtensions {

    override fun getSource(): BaseSource = source

    /** 调用顶层函数并归一化返回值;函数缺失抛出明确错误 */
    fun callFunction(name: String, args: List<Pair<String, Any?>>): String? {
        val scope = buildScope(args)
        if (ScriptableObject.getProperty(scope, name) !is JsFunction) {
            throw NoStackTraceException("JS源缺少函数 $name")
        }
        val callExpr = "$name(${args.joinToString(", ") { it.first }})"
        val raw = compile(callExpr).eval(scope, coroutineContext)
        return normalizeJsResult(raw, coroutineContext)
    }

    /** 调用可选顶层函数:缺失返回 null(单次 eval,不做 hasFunction 预探测) */
    fun callFunctionIfExists(name: String, args: List<Pair<String, Any?>>): String? {
        val scope = buildScope(args)
        if (ScriptableObject.getProperty(scope, name) !is JsFunction) {
            return null
        }
        val callExpr = "$name(${args.joinToString(", ") { it.first }})"
        val raw = compile(callExpr).eval(scope, coroutineContext)
        return normalizeJsResult(raw, coroutineContext)
    }

    private fun buildScope(args: List<Pair<String, Any?>>): Scriptable {
        val mainJs = source.mainJs
        if (mainJs.isNullOrBlank()) throw NoStackTraceException("mainJs 为空,不是JS源")
        val bindings = buildScriptBindings { b ->
            b["java"] = this
            b["source"] = source
            b["baseUrl"] = source.getKey()
            b["cookie"] = CookieStore
            b["cache"] = CacheManager
            args.forEach { (k, v) -> b[k] = v }
        }
        val shared = source.getShareScope(coroutineContext)
            ?: SharedJsScope.getCryptoScope(coroutineContext)
        val scope = if (shared == null) {
            RhinoScriptEngine.getRuntimeScope(bindings)
        } else {
            bindings.apply { prototype = shared }
        }
        compile(mainJs).eval(scope, coroutineContext)
        return scope
    }

    companion object {

        /**
         * 主脚本与调用表达式共用的编译缓存。AnalyzeRule 的先例是每个实例一个 HashMap +
         * getOrPutLimit(16)(实例私有,天然无并发问题);这里选 androidx LruCache(64) 是因为
         * scriptCache 挂在 companion object 上,被所有 JsSourceEngine 实例/线程全局共享,
         * 需要自带同步的实现,LruCache 内部方法自带 synchronized。多源并发下 16 格易被
         * 主脚本+调用表达式挤爆致反复重编译,扩容到 64。
         */
        private val scriptCache = LruCache<String, CompiledScript>(64)

        private fun compile(js: String): CompiledScript {
            scriptCache[js]?.let { return it }
            return RhinoScriptEngine.compile(js).also { scriptCache.put(js, it) }
        }

        /**
         * JS 返回值归一化(spec §2-5):String 原样;null/Undefined→null;
         * NativeObject/NativeArray 等 Scriptable 改走 JS 引擎自身 JSON.stringify(回退方案,见下);
         * 其余包装对象走 GSON——BaseSource.loginUi 先例。
         * [coroutineContext] 可选,转发给 stringifyScriptable 用于在 stringify 执行期间
         * (JS 侧自定义 toJSON/getter 可能耗时)传递协程取消信号。
         */
        fun normalizeJsResult(result: Any?, coroutineContext: CoroutineContext? = null): String? {
            var value = result
            if (value is Wrapper) value = value.unwrap()
            return when {
                value == null || value is Undefined -> null
                value is String -> value
                value is CharSequence -> value.toString()
                value is Scriptable ->
                    stringifyScriptable(value, coroutineContext) ?: GSON.toJson(value)

                else -> GSON.toJson(value)
            }
        }

        /**
         * 回退方案(简报 Step 4 预案,由 JsSourceCallProbeTest.evalCallExpressionInvokesTopLevelFunction
         * 探针实测触发):GSON 反射不认识 Rhino 内部惰性类型,'u' + page 这类字符串拼接产生的是
         * ConsString,嵌套在 NativeArray/NativeObject 属性里时 GSON 会把它当成普通 Java 对象反射出
         * {left,right,length,isFlat} 内部字段,而不是拼接后的文本。改走 JS 引擎自身的
         * JSON.stringify——通过 Rhino Function.call 调用全局 JSON.stringify,不依赖猜测
         * NativeJSON 类的具体包路径。对纯数据(字面量对象/数组)与书源手写
         * `JSON.stringify(...)`(jsonStringifyEquivalentToDirectReturn 探针)结果一致;
         * 差异在返回值带自定义 toJSON/getter 时:书源内手写走 eval 通道(allowScriptRun 开)
         * 可执行它们,此处临时 Context 闸门关闭,解释执行会被 doTopCall 拦下并包装成
         * NoStackTraceException 明确报错——这是有意的:为 toJSON 开闸等价于变相 invokeMethod,
         * 正是 spec §7-4 回避的通道。契约=返回纯数据。
         *
         * 这里的调用点在 eval 之外,没有活跃 Context 可复用,只能自行 Context.enter()/exit()
         * 独立进入一个临时 Context——与 JsExtensions.singleFlight/lock(action.call(cx, ...)
         * 里的 cx 取自调用方当前正在执行的活跃 rhinoContext,不新建)是不同款的处理方式。
         * 进入临时 Context 后会把外部传入的 [coroutineContext] 写入其 coroutineContext 字段
         * (同 RhinoScriptEngine.eval 的注入/还原写法)。注意:当前闸门关闭下 toJSON/getter
         * 根本执行不到(见上),此注入在现有调用路径上实际不可达,保留是为将来若有受控开闸
         * 场景时取消信号即刻生效,不是活语义。
         *
         * 找不到 JSON/JSON.stringify(理论上不会发生,标准对象总会初始化)时返回 null 交给
         * 调用方回退 GSON;但 stringify 执行本身抛异常(典型如返回值含循环引用)会包装成
         * NoStackTraceException 明确抛出、不再静默回退 GSON——GSON 反射遇循环引用会栈溢出,
         * 明确报错优于栈炸。
         */
        private fun stringifyScriptable(
            value: Scriptable,
            coroutineContext: CoroutineContext? = null,
        ): String? {
            val topScope = ScriptableObject.getTopLevelScope(value) ?: return null
            val json = ScriptableObject.getProperty(topScope, "JSON") as? Scriptable ?: return null
            val stringify = ScriptableObject.getProperty(json, "stringify") as? JsFunction ?: return null
            val cx = Context.enter() as RhinoContext
            val previousCoroutineContext = cx.coroutineContext
            if (coroutineContext != null && coroutineContext[Job] != null) {
                cx.coroutineContext = coroutineContext
            }
            try {
                val raw = try {
                    stringify.call(cx, topScope, json, arrayOf<Any?>(value))
                } catch (e: Exception) {
                    throw NoStackTraceException("JS返回值 JSON.stringify 失败: ${e.message}")
                }
                return RhinoScriptEngine.unwrapReturnValue(raw) as? String
            } finally {
                cx.coroutineContext = previousCoroutineContext
                Context.exit()
            }
        }
    }
}
