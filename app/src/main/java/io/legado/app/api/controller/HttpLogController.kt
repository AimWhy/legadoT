package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.help.config.AppConfig
import io.legado.app.model.HttpLogger

/**
 * HTTP 请求日志只读暴露:摘要列表 + 按 id 取完整记录。
 * 记录本身由 HttpLogInterceptor 在 AppConfig.recordHttpLog 开启时写入,
 * recording 字段让调用方能区分"没有请求"与"开关未开"。
 */
object HttpLogController {

    fun getLogs(parameters: Map<String, List<String>>): ReturnData {
        val limit = (parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 50).coerceAtLeast(1)
        val summaries = HttpLogger.logs.take(limit).map {
            mapOf(
                "id" to it.id,
                "time" to it.time,
                "method" to it.method,
                "url" to it.url,
                "statusCode" to it.statusCode,
                "duration" to it.duration,
                "error" to it.error,
            )
        }
        return ReturnData().setData(
            mapOf(
                "recording" to AppConfig.recordHttpLog,
                "logs" to summaries,
            )
        )
    }

    fun getLog(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val id = parameters["id"]?.firstOrNull()?.toLongOrNull()
            ?: return returnData.setErrorMsg("参数id不能为空")
        val record = HttpLogger.getById(id)
            ?: return returnData.setErrorMsg("未找到 HTTP 记录 #$id")
        return returnData.setData(record)
    }
}
