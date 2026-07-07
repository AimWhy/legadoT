package io.legado.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 11 哨兵：CodeView 语义高亮色主题化 + 去抖 + 悬崖放宽。
 *
 * - CodeViewExtensions 不再直接引用 md_* 材料色，改用 code_* 语义色（日夜双态)。
 * - values/colors.xml 与 values-night/colors.xml 均需定义全部 5 个 code_* 语义色名称。
 * - CodeView 高亮悬崖阈值提取为具名常量 HIGHLIGHT_MAX_LENGTH = 8192。
 * - onTextChanged 分支补上 cancelHighlighterRender() 去抖，对齐 afterTextChanged 既有写法。
 */
class CodeHighlightThemeTest {

    private val codeColorNames = listOf(
        "code_rule_symbol",
        "code_json_token",
        "code_escape",
        "code_operator",
        "code_keyword",
    )

    @Test
    fun `CodeViewExtensions no longer references md_ material colors`() {
        val src = readProjectFile("src/main/java/io/legado/app/ui/widget/code/CodeViewExtensions.kt")
        assertFalse("CodeViewExtensions 不应再引用 md_* 材料色", src.contains("md_"))
    }

    @Test
    fun `day colors xml defines all code_ semantic colors`() {
        val xml = readProjectFile("src/main/res/values/colors.xml")
        codeColorNames.forEach { name ->
            assertTrue("values/colors.xml 缺少 $name", xml.contains("name=\"$name\""))
        }
    }

    @Test
    fun `night colors xml defines all code_ semantic colors`() {
        val xml = readProjectFile("src/main/res/values-night/colors.xml")
        codeColorNames.forEach { name ->
            assertTrue("values-night/colors.xml 缺少 $name", xml.contains("name=\"$name\""))
        }
    }

    @Test
    fun `CodeView extracts highlight cliff to named constant`() {
        val src = readProjectFile("src/main/java/io/legado/app/ui/widget/code/CodeView.kt")
        assertTrue("CodeView 应含具名常量", src.contains("HIGHLIGHT_MAX_LENGTH = 8192"))
    }

    @Test
    fun `onTextChanged debounces like afterTextChanged`() {
        val src = readProjectFile("src/main/java/io/legado/app/ui/widget/code/CodeView.kt")
        val onTextChangedStart = src.indexOf("override fun onTextChanged(")
        assertTrue("onTextChanged 未找到", onTextChangedStart >= 0)
        val onTextChangedEnd = src.indexOf("override fun afterTextChanged(", onTextChangedStart)
        assertTrue("afterTextChanged 未找到", onTextChangedEnd >= 0)
        val onTextChangedBody = src.substring(onTextChangedStart, onTextChangedEnd)
        assertTrue(
            "onTextChanged 应在 postDelayed 前调用 cancelHighlighterRender() 去抖",
            onTextChangedBody.contains("cancelHighlighterRender()")
        )
        val cancelIndex = onTextChangedBody.indexOf("cancelHighlighterRender()")
        val postDelayedIndex = onTextChangedBody.indexOf("postDelayed")
        assertTrue("cancelHighlighterRender() 未找到", cancelIndex >= 0)
        assertTrue("postDelayed 未找到", postDelayedIndex >= 0)
        assertTrue(
            "cancelHighlighterRender() 必须在 postDelayed 之前调用，去抖才生效",
            cancelIndex < postDelayedIndex
        )
    }

    private fun readProjectFile(path: String): String = File(path).readText()
}
