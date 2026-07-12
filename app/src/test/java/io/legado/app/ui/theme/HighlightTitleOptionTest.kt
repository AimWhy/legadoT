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
        assertTrue("DB 版本应升到 85", db.contains("version = 85"))
        assertTrue("应有 84→85 AutoMigration", db.contains("AutoMigration(from = 84, to = 85)"))
    }
}
