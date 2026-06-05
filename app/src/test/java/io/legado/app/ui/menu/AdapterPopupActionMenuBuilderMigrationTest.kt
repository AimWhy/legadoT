package io.legado.app.ui.menu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdapterPopupActionMenuBuilderMigrationTest {

    @Test
    fun `popup action menu builder provides shared vertical menu defaults`() {
        val kt = readProjectFile("src/main/java/io/legado/app/ui/widget/PopupActionMenu.kt")

        assertContains("PopupActionMenu.kt", kt, "class PopupActionMenuBuilder")
        assertContains("PopupActionMenu.kt", kt, "fun item(")
        assertContains("PopupActionMenu.kt", kt, "fun danger(vararg values: String)")
        assertContains("PopupActionMenu.kt", kt, "PopupAction(context).apply")
        assertContains("PopupActionMenu.kt", kt, "setVertical(true)")
        assertContains("PopupActionMenu.kt", kt, "setDangerValues(dangerValues)")
        assertContains("PopupActionMenu.kt", kt, "showAsDropDown(anchor, 0, yOffset)")
    }

    @Test
    fun `adapter row menus use popup action menu builder instead of platform popup menu`() {
        adapterMenuFiles.forEach { path ->
            val kt = readProjectFile(path)

            assertFalse("$path should not import PopupMenu", kt.contains("import android.widget.PopupMenu"))
            assertFalse("$path should not import AppCompat PopupMenu", kt.contains("import androidx.appcompat.widget.PopupMenu"))
            assertFalse("$path should not instantiate PopupMenu", kt.contains("PopupMenu(context, view)"))
            assertFalse("$path should not inflate menu resources into PopupMenu", kt.contains(".inflate(R.menu."))
            assertContains(path, kt, "popupActionMenu(context)")
            assertContains(path, kt, ".show(view)")
        }
    }

    @Test
    fun `adapter row menus keep danger action styling`() {
        mapOf(
            "src/main/java/io/legado/app/ui/main/rss/RssAdapter.kt" to "danger(\"del\")",
            "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt" to "danger(\"del\")",
            "src/main/java/io/legado/app/ui/rss/subscription/RuleSubAdapter.kt" to "danger(\"del\")",
            "src/main/java/io/legado/app/ui/replace/ReplaceRuleAdapter.kt" to "danger(\"del\")",
            "src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt" to "danger(\"del\")",
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskAdapter.kt" to "danger(\"delete\")"
        ).forEach { (path, expected) ->
            assertContains(path, readProjectFile(path), expected)
        }
    }

    @Test
    fun `adapter row menus preserve action values and callbacks`() {
        assertMenuActions(
            "src/main/java/io/legado/app/ui/main/rss/RssAdapter.kt",
            "top" to "callBack.toTop(rssSource)",
            "edit" to "callBack.edit(rssSource)",
            "disable" to "callBack.disable(rssSource)",
            "del" to "callBack.del(rssSource)"
        )
        assertMenuActions(
            "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
            "top" to "callBack.toTop(source)",
            "bottom" to "callBack.toBottom(source)",
            "del" to "callBack.del(source)"
        )
        assertMenuActions(
            "src/main/java/io/legado/app/ui/rss/subscription/RuleSubAdapter.kt",
            "del" to "callBack.delSubscription(source)"
        )
        assertMenuActions(
            "src/main/java/io/legado/app/ui/replace/ReplaceRuleAdapter.kt",
            "top" to "callBack.toTop(item)",
            "bottom" to "callBack.toBottom(item)",
            "del" to "callBack.delete(item)"
        )
        assertMenuActions(
            "src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt",
            "top" to "callBack.toTop(source)",
            "bottom" to "callBack.toBottom(source)",
            "del" to "callBack.del(source)"
        )
        assertMenuActions(
            "src/main/java/io/legado/app/ui/autoTask/AutoTaskAdapter.kt",
            "login" to "context.startActivity<SourceLoginActivity>",
            "log" to "callBack.showLog(task)",
            "delete" to "callBack.delete(task)"
        )
    }

    @Test
    fun `adapter row menus keep dynamic visibility and side effects`() {
        val autoTask = readProjectFile("src/main/java/io/legado/app/ui/autoTask/AutoTaskAdapter.kt")
        val rssSource = readProjectFile("src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt")
        val replaceRule = readProjectFile("src/main/java/io/legado/app/ui/replace/ReplaceRuleAdapter.kt")
        val txtTocRule = readProjectFile("src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt")

        assertContains("AutoTaskAdapter.kt", autoTask, "visible = !task.loginUrl.isNullOrBlank()")
        assertContains("AutoTaskAdapter.kt", autoTask, "context.startActivity<SourceLoginActivity>")
        assertContains("AutoTaskAdapter.kt", autoTask, "putExtra(\"type\", \"autoTask\")")
        assertContains("RssSourceAdapter.kt", rssSource, "selected.remove(source)")
        assertContains("ReplaceRuleAdapter.kt", replaceRule, "selected.remove(item)")
        assertContains("TxtTocRuleAdapter.kt", txtTocRule, "selected.remove(source)")
    }

    private val adapterMenuFiles = listOf(
        "src/main/java/io/legado/app/ui/main/rss/RssAdapter.kt",
        "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        "src/main/java/io/legado/app/ui/rss/subscription/RuleSubAdapter.kt",
        "src/main/java/io/legado/app/ui/replace/ReplaceRuleAdapter.kt",
        "src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt",
        "src/main/java/io/legado/app/ui/autoTask/AutoTaskAdapter.kt"
    )

    private fun assertMenuActions(path: String, vararg actions: Pair<String, String>) {
        val kt = readProjectFile(path)
        actions.forEach { (value, callback) ->
            assertBuilderAction(path, kt, value, callback)
        }
    }

    private fun assertBuilderAction(name: String, text: String, value: String, callback: String) {
        val expected = "\"$value\""
        assertTrue(
            "$name should contain item(...) with value $expected",
            itemCalls(text).any { it.contains(expected) }
        )
        assertTrue(
            "$name should contain callback $callback",
            text.contains(callback)
        )
    }

    private fun itemCalls(text: String): List<String> {
        val calls = mutableListOf<String>()
        var searchIndex = 0
        while (true) {
            val start = text.indexOf("item(", searchIndex)
            if (start == -1) break

            var index = start + "item(".length
            var depth = 1
            var inString = false
            var escaped = false
            while (index < text.length && depth > 0) {
                val char = text[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                } else {
                    when (char) {
                        '"' -> inString = true
                        '(' -> depth++
                        ')' -> depth--
                    }
                }
                index++
            }

            while (index < text.length && text[index].isWhitespace()) {
                index++
            }

            if (index < text.length && text[index] == '{') {
                var blockDepth = 1
                index++
                while (index < text.length && blockDepth > 0) {
                    val char = text[index]
                    if (inString) {
                        when {
                            escaped -> escaped = false
                            char == '\\' -> escaped = true
                            char == '"' -> inString = false
                        }
                    } else {
                        when (char) {
                            '"' -> inString = true
                            '{' -> blockDepth++
                            '}' -> blockDepth--
                        }
                    }
                    index++
                }
            }

            if (depth == 0) {
                calls += text.substring(start, index)
            }
            searchIndex = index.coerceAtLeast(start + 1)
        }
        return calls
    }

    private fun assertContains(name: String, text: String, expected: String) {
        assertTrue("$name should contain $expected", text.contains(expected))
    }

    private fun readProjectFile(pathInApp: String): String {
        val candidates = listOf(
            File(pathInApp),
            File("app/$pathInApp")
        )
        return candidates.firstOrNull { it.isFile }?.readText().orEmpty()
    }
}
