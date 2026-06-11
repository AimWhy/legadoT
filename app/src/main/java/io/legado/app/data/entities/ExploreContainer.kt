package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 发现页容器:绑定某书源的某个发现分类,自选展示样式
 */
@Entity(tableName = "exploreContainers")
data class ExploreContainer(
    @PrimaryKey(autoGenerate = true)
    var id: Long = System.currentTimeMillis(),
    /** 书源标识 bookSourceUrl */
    var sourceUrl: String = "",
    /** 书源名快照(显示用) */
    var sourceName: String = "",
    /** 添加时的分类名,用于显示和在当前分类列表中动态匹配 */
    var kindTitle: String = "",
    /** 添加时的分类 URL 快照(动态匹配不到时兜底) */
    var kindUrl: String = "",
    /** 自定义标题,null/空白时显示 kindTitle */
    var customTitle: String? = null,
    /** 展示样式 */
    var style: Int = STYLE_FLOW,
    /** 列表样式展示数量(横滑样式忽略) */
    var listCount: Int = 3,
    var sortOrder: Int = 0,
    var enabled: Boolean = true,
) {

    fun getDisplayTitle(): String {
        return customTitle?.takeUnless { it.isBlank() } ?: kindTitle
    }

    companion object {
        /** 横滑封面 */
        const val STYLE_FLOW = 0
        /** 列表带简介 */
        const val STYLE_LIST = 1
    }
}
