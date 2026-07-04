package io.legado.app.base.adapter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R1d 多选样板收敛锚:6 个管理页 adapter 的 selectAll/revertSelection/selection 集中到
 * [SelectableAdapter] 默认方法,本地不再各自手写(手写会与接口默认实现漂移)。
 */
class SelectableAdapterMigrationTest {

    private val adapters = listOf(
        "src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt",
        "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        "src/main/java/io/legado/app/ui/replace/ReplaceRuleAdapter.kt",
        "src/main/java/io/legado/app/ui/dict/rule/DictRuleAdapter.kt",
        "src/main/java/io/legado/app/ui/book/toc/rule/TxtTocRuleAdapter.kt",
        "src/main/java/io/legado/app/ui/autoTask/AutoTaskAdapter.kt",
    )

    @Test
    fun `six manage adapters implement SelectableAdapter and drop local select boilerplate`() {
        adapters.forEach { path ->
            val kt = readProjectFile(path)
            assertTrue(
                "$path 应实现 SelectableAdapter/SimpleSelectableAdapter",
                kt.contains("SelectableAdapter<") || kt.contains("SimpleSelectableAdapter<")
            )
            assertTrue("$path 应声明 override val selectedKeys", kt.contains("override val selectedKeys"))
            // 全选/反选样板已由接口默认方法提供,本地不再手写(双份会漂移)
            assertFalse("$path 仍手写 fun selectAll()", kt.contains("fun selectAll()"))
            assertFalse("$path 仍手写 fun revertSelection()", kt.contains("fun revertSelection()"))
            // 旧的私有可变集合字段已退场(改名 selectedKeys 并上移接口契约)
            assertFalse(
                "$path 仍持有旧 selected/selectedIds 私有字段",
                kt.contains("private val selected =") || kt.contains("private val selectedIds =")
            )
        }
    }

    @Test
    fun `interval selection stays only where it existed`() {
        // checkSelectedInterval 仅 BookSource/RssSource 有,不进通用接口(4 页不需要),保留在各自类内
        listOf(
            "src/main/java/io/legado/app/ui/book/source/manage/BookSourceAdapter.kt",
            "src/main/java/io/legado/app/ui/rss/source/manage/RssSourceAdapter.kt",
        ).forEach {
            assertTrue("$it 应保留 checkSelectedInterval", readProjectFile(it).contains("fun checkSelectedInterval()"))
        }
        assertFalse(
            "SelectableAdapter 不应含 checkSelectedInterval(覆盖面不全,不进通用契约)",
            readProjectFile("src/main/java/io/legado/app/base/adapter/SelectableAdapter.kt")
                .contains("checkSelectedInterval")
        )
    }

    private fun readProjectFile(pathInApp: String): String {
        return listOf(File(pathInApp), File("app/$pathInApp")).first { it.isFile }.readText()
    }
}
