package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 章节角色标注缓存。contentMd5 对最终朗读文本计算, 正文净化或替换规则一变即失效重标。
 */
@Entity(tableName = "chapterRoleScripts", primaryKeys = ["bookUrl", "chapterIndex"])
data class ChapterRoleScript(
    val bookUrl: String = "",
    val chapterIndex: Int = 0,
    val contentMd5: String = "",
    val segmentsJson: String = "",
    @ColumnInfo(defaultValue = "0")
    val createTime: Long = System.currentTimeMillis()
)
