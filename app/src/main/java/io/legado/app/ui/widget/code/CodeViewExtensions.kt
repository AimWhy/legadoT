@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.utils.getCompatColor
import java.util.regex.Pattern

val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:")
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")
val wrapPattern: Pattern = Pattern.compile("\\\\n")
val operationPattern: Pattern =
    Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\-|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")
val jsPattern: Pattern = Pattern.compile(
    "\\b(var|let|const|function|return|if|else|for|while|do|break|continue|switch|case|default|" +
        "try|catch|finally|throw|new|delete|typeof|instanceof|in|of|void|this|" +
        "true|false|null|undefined)\\b"
)

/**
 * 语义高亮色取自调用方（CodeView）自身的 [Context]，而非 appCtx —— 保证在夜间模式下解析到
 * values-night/colors.xml 中的 code_* 覆盖值。
 *
 * 颜色在 pattern 注册时捕获——两个编辑 adapter 于 onCreateViewHolder 注册（每次日夜切换 recreate 重建
 * adapter 取新色），两个弹窗消费者（CodeDialog/HttpTtsEditDialog）于 Fragment 创建时注册；
 * 不得在 onBindViewHolder 注册（每次回收重绑会重复注册）。
 */
fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, context.getCompatColor(R.color.code_rule_symbol))
}

fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, context.getCompatColor(R.color.code_json_token))
}

fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, context.getCompatColor(R.color.code_escape))
    addSyntaxPattern(operationPattern, context.getCompatColor(R.color.code_operator))
    addSyntaxPattern(jsPattern, context.getCompatColor(R.color.code_keyword))
}

fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}