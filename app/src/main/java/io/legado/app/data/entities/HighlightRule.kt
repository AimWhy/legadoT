package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.legado.app.help.HighlightStyle
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 关键词/正则自动高亮规则
 * style = HighlightStyle 的 JSON(见 [HighlightStyle]);scope 语义同 ReplaceRule:
 * 空 = 全局,非空 = 按书名/书源(origin)子串限定。
 */
@Parcelize
@Entity(tableName = "highlightRules")
data class HighlightRule(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String = "",
    var pattern: String = "",
    var isRegex: Boolean = false,
    var scope: String? = null,
    var isEnabled: Boolean = true,
    var style: String = "",
    @ColumnInfo(name = "sortOrder")
    var order: Int = Int.MIN_VALUE,
    var timeoutMillisecond: Long = 3000L,
    var group: String? = null
) : Parcelable {

    override fun equals(other: Any?): Boolean {
        if (other is HighlightRule) return other.id == id
        return super.equals(other)
    }

    override fun hashCode(): Int = id.hashCode()

    @IgnoredOnParcel
    @Ignore
    @Transient
    private var styleCache: HighlightStyle? = null

    /** 解析后的样式(惰性缓存) */
    fun styleObj(): HighlightStyle {
        styleCache?.let { return it }
        return (GSON.fromJsonObject<HighlightStyle>(style).getOrNull() ?: HighlightStyle())
            .also { styleCache = it }
    }

    /** 写入样式并同步 JSON 列 */
    fun applyStyle(s: HighlightStyle) {
        styleCache = s
        style = GSON.toJson(s)
    }

    fun getDisplayName(): String = name.ifBlank { pattern }

    fun isValid(): Boolean {
        if (pattern.isEmpty()) return false
        if (isRegex) {
            try {
                Pattern.compile(pattern)
            } catch (_: PatternSyntaxException) {
                return false
            }
            if (pattern.endsWith('|') && !pattern.endsWith("\\|")) return false
        }
        return true
    }

    fun checkValid() {
        if (!isValid()) {
            throw io.legado.app.exception.NoStackTraceException("规则无效: $pattern")
        }
    }
}
