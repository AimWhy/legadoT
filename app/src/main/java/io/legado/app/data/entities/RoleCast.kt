package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 角色到音色的分配, 按书维护。voice 为空表示用引擎默认音色。
 */
@Entity(tableName = "roleCasts", primaryKeys = ["bookUrl", "roleName"])
data class RoleCast(
    val bookUrl: String = "",
    val roleName: String = "",
    @ColumnInfo(defaultValue = "0")
    var ttsEngineId: Long = 0,
    var voice: String? = null,
    var gender: String? = null,
    var ageGroup: String? = null,
    /** 用户手动改过, 自动配角不再覆盖 */
    @ColumnInfo(defaultValue = "0")
    var isManual: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    var lastSeenChapter: Int = 0
) {
    companion object {
        const val NARRATOR = "旁白"
    }
}
