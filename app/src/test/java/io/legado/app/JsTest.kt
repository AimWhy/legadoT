package io.legado.app

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BookChapter
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.junit.Test

class JsTest {

    @Language("js")
    private val printJs = """
        function print(str, newline) {
            if (typeof(str) == 'undefined') {
                str = 'undefined';
            } else if (str == null) {
                str = 'null';
            } 
            java.lang.System.out.print(String(str));
            if (newline) java.lang.System.out.print("\n");
        }
        function println(str) { 
            print(str, true);
        }
    """.trimIndent()

    @Test
    fun testMap() {
        val map = hashMapOf("id" to "3242532321")
        val bindings = ScriptBindings()
        bindings["result"] = map
        @Language("js")
        val jsMap = "$=result;id=$.id;id"
        val result = RhinoScriptEngine.eval(jsMap, bindings)
        Assert.assertEquals("3242532321", result)
        @Language("js")
        val jsMap1 = """result.get("id")"""
        val result1 = RhinoScriptEngine.eval(jsMap1, bindings)
        Assert.assertEquals("3242532321", result1)
    }

    @Test
    fun testFor() {
        val scope = RhinoScriptEngine.run {
            val scope = getRuntimeScope(ScriptBindings())
            eval(printJs, scope)
            scope
        }

        @Language("js")
        val jsFor = """
            let result = 0
            let a=[1,2,3]
            let l=a.length
            for (let i = 0;i<l;i++){
            	result = result + a[i]
                println(i)
            }
            for (let o of a){
            	result = result + o
                println(o)
            }
            for (let o in a){
            	result = result + o
                println(o)
            }
            result
        """.trimIndent()
        val result = RhinoScriptEngine.eval(jsFor, scope)
        Assert.assertEquals("12012", result)
    }

    @Test
    fun testReturnNull() {
        val result = RhinoScriptEngine.eval("null")
        Assert.assertEquals(null, result)
    }

    @Test
    fun testReplace() {
        @Language("js")
        val js = """
            s=result.match(/(.{1,6}?)(第.*)/);
            n=s[2].length-parseInt(6-s[1].length);
            s[2].substr(0,n);
        """.trimIndent()
        val x = RhinoScriptEngine.run {
            val bindings = ScriptBindings()
            bindings["result"] = "筳彩涫第七百一十四章 人头树鮺舦綸"
            eval(js, bindings)
        }
        Assert.assertEquals(x, "第七百一十四章 人头树")
    }


    @Test
    fun chapterText() {
        val chapter = BookChapter(title = "xxxyyy")
        val bindings = ScriptBindings()
        bindings["chapter"] = chapter
        @Language("js")
        val js = "chapter.title"
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, "xxxyyy")
    }

    @Test
    fun javaListForEach() {
        val list = arrayListOf(1, 2, 3)
        val bindings = ScriptBindings()
        bindings["list"] = list
        @Language("js")
        val js = """
            var result = 0
            list.forEach(item => {result = result + item})
            result
        """.trimIndent()
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, 6.0)
    }

    /**
     * ES6 兼容性边界探针(2026-07-04 实测锚定),书源 skill 的兼容表依据
     * (legado-source-skill/references/js-api.md)。引擎(htmlunit-core-js)升级后
     * 若本测试翻红,说明支持边界变了,同步更新兼容表。
     */
    @Test
    fun es6CompatBoundary() {
        // 解析期报错的语法:class / async / 函数调用展开 / 数组剩余解构
        listOf(
            "class A { }; new A()",
            "typeof (async () => 42)",
            "Math.max(...[1, 2, 5])",
            "let [a, ...b] = [1, 2, 3]; b.join('-')",
        ).forEach { js ->
            val outcome = runCatching { RhinoScriptEngine.eval(js, ScriptBindings()) }
            Assert.assertTrue("引擎已支持(原判不支持): $js", outcome.isFailure)
        }
        // Promise 构造器存在、then 可注册,但无事件循环微任务不排水——回调从不执行,
        // Promise 链在书源 JS 里实际不可用(勿作为 async/await 的替代方案)
        @Language("js")
        val promiseJs = "var r = 0; Promise.resolve(7).then(function(v){ r = v }); '' + r"
        Assert.assertEquals("0", RhinoScriptEngine.eval(promiseJs, ScriptBindings()))
    }

    /**
     * ES6+ 支持面正面清单(es6CompatBoundary 的反面):模板/文档允许使用的新语法
     * 逐条锚定在此,升级 rhino 版本后跑此测试重新定界。
     * 每条求值结果同时断言,防"解析通过但语义错"。
     */
    @Test
    fun es6SupportedFeatures() {
        listOf(
            // 语法 to 期望值
            "let x = 1; const y = 2; '' + (x + y)" to "3",
            "var f = (a, b) => a + b; '' + f(1, 2)" to "3",
            "var n = 6; `p\${n}q`" to "p6q",
            "var s = 0; for (var v of [1, 2, 3]) s += v; '' + s" to "6",
            "var {a, b} = {a: 1, b: 2}; '' + (a + b)" to "3",
            "var [p, q] = [7, 8]; '' + (p + q)" to "15",
            "function g(a, b) { b = b || 10; return a + b }; '' + g(5)" to "15",
            "var k = 'dyn'; var o = {[k]: 9, m() { return this[k] } }; '' + o.m()" to "9",
            "var arr = [1, 2]; var arr2 = [0].concat(arr); '' + arr2.length" to "3",
            "'' + [3, 1, 2].includes(2)" to "true",
            "'' + Object.assign({}, {a: 1}, {b: 2}).b" to "2",
            "'' + Array.from('ab').length" to "2",
            "'' + 'x'.repeat(3)" to "xxx",
            "'' + '5'.padStart(3, '0')" to "005",
            "function d(a, b = 10) { return a + b }; '' + d(5)" to "15",
            "var a1 = [1, 2]; var a2 = [0, ...a1]; '' + a2.length" to "3",
            "var o1 = {a: 1}; var o2 = {...o1, b: 2}; '' + (o2.a + o2.b)" to "3",
            "var u = {v: {w: 5}}; '' + (u.v?.w)" to "5",
            "var z = null; '' + (z ?? 'dft')" to "dft",
            "function r(a, ...rest) { return '' + rest.length }; r(1, 2, 3)" to "2",
        ).forEach { (js, expect) ->
            val outcome = runCatching { RhinoScriptEngine.eval(js, ScriptBindings()) }
            Assert.assertEquals("求值失败或结果不符: $js -> ${outcome.exceptionOrNull()?.message}",
                expect, outcome.getOrNull())
        }
        // 顶层声明可见性:JsSourceConfig.extract 经 ScriptableObject.getProperty 取
        // source/函数,var/let/const 三种顶层声明均须可见
        listOf("var", "let", "const").forEach { kw ->
            val scope = RhinoScriptEngine.getRuntimeScope(ScriptBindings())
            RhinoScriptEngine.eval("$kw source = { a: 1 }", scope)
            val found = org.htmlunit.corejs.javascript.ScriptableObject.getProperty(scope, "source")
            Assert.assertNotEquals("顶层 $kw 声明经 getProperty 不可见",
                org.htmlunit.corejs.javascript.Scriptable.NOT_FOUND, found)
        }
    }

    /**
     * Jsoup 经顶层包对象可达(RhinoClassShutter 未拦 org.jsoup)——
     * 纯JS单文件源(java=JsExtensions,无 AnalyzeRule 选择器函数)的 HTML 解析通道。
     */
    @Test
    fun jsoupFromJsScope() {
        @Language("js")
        val js = """
            var doc = org.jsoup.Jsoup.parse('<div><a class="t">x</a><a class="t">y</a></div>')
            var out = []
            for (var e of doc.select('a.t')) out.push(e.text())
            out.join(',')
        """.trimIndent()
        Assert.assertEquals("x,y", RhinoScriptEngine.eval(js, ScriptBindings()))
    }

    @Test
    fun typeofString() {
        val bindings = ScriptBindings()
        @Language("js")
        val js = """
            s = "" + String()
            typeof s
        """.trimIndent()
        val result = RhinoScriptEngine.eval(js, bindings)
        Assert.assertEquals(result, "string")
    }

}