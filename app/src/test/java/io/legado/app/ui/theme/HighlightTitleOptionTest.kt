package io.legado.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 高亮规则「应用于标题」结构哨兵 */
class HighlightTitleOptionTest {
    @Test
    fun `highlight rule has applyToTitle column and db migrated`() {
        val entity = File("src/main/java/io/legado/app/data/entities/HighlightRule.kt").readText()
        assertTrue("HighlightRule 应有 applyToTitle 列", entity.contains("var applyToTitle"))
        val db = File("src/main/java/io/legado/app/data/AppDatabase.kt").readText()
        assertTrue("DB 版本应升到 86", db.contains("version = 86"))
        assertTrue("应有 84→85 AutoMigration", db.contains("AutoMigration(from = 84, to = 85)"))
        assertTrue("应有 85→86 AutoMigration", db.contains("AutoMigration(from = 85, to = 86)"))
    }

    @Test
    fun `edit dialog has apply to title checkbox wired`() {
        val layout = File("src/main/res/layout/dialog_highlight_rule_edit.xml").readText()
        assertTrue("布局应有 cb_apply_to_title", layout.contains("@+id/cb_apply_to_title"))
        val dialog = File("src/main/java/io/legado/app/ui/highlight/edit/HighlightRuleEditDialog.kt").readText()
        assertTrue("upView 应回填 applyToTitle", dialog.contains("cbApplyToTitle.isChecked = r.applyToTitle"))
        assertTrue("getRule 应读 applyToTitle", dialog.contains("r.applyToTitle = cbApplyToTitle.isChecked"))
    }
}
