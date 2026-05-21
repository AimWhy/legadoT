package io.legado.app.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HttpRecord(
    val id: Long,
    val time: Long,
    val method: String,
    val url: String,
    val statusCode: Int,
    val duration: Long,
    val requestHeaders: String,
    val requestBody: String,
    val responseHeaders: String,
    val responseBody: String,
    val error: String?
) {
    val summary: String
        get() = buildString {
            append("[#$id] $method $url -> $statusCode ${duration}ms")
            if (!error.isNullOrBlank()) append(" | $error")
        }

    val fullDetail: String
        get() = buildString {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            appendLine("=== HTTP Request Detail ===")
            appendLine("Time: ${df.format(Date(time))}")
            appendLine("Duration: ${duration}ms")
            appendLine()
            appendLine("-- Request --")
            appendLine("$method $url")
            appendLine(requestHeaders)
            if (requestBody.isNotBlank()) {
                appendLine()
                appendLine(requestBody)
            }
            appendLine()
            appendLine("-- Response --")
            appendLine("Status: $statusCode")
            appendLine(responseHeaders)
            if (responseBody.isNotBlank()) {
                appendLine()
                appendLine(responseBody)
            }
            if (!error.isNullOrBlank()) {
                appendLine()
                appendLine("-- Error --")
                appendLine(error)
            }
        }

    companion object {
        /** 日志前缀，AppLogDialog 据此识别 HTTP 日志条目 */
        const val LOG_PREFIX = "\uD83C\uDF10"

        /** 从日志消息中解析记录 ID（格式：🌐 [#123] ...） */
        fun parseIdFromLog(message: String): Long? {
            val start = message.indexOf("[#")
            if (start < 0) return null
            val numStart = start + 2
            val end = message.indexOf(']', numStart)
            if (end < 0) return null
            return message.substring(numStart, end).toLongOrNull()
        }
    }
}
