package io.legado.app.help.ai

import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.postJson
import io.legado.app.utils.GSON
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readString
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * OpenAI 兼容的 chat completions 调用。只负责协议, 不认识角色与朗读。
 */
object AiClient {

    private const val PATH = "/chat/completions"

    fun isConfigured(): Boolean =
        AppConfig.aiBaseUrl.isNotBlank() && AppConfig.aiModel.isNotBlank()

    fun endpointOf(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (trimmed.endsWith(PATH)) trimmed else trimmed + PATH
    }

    fun extractContent(responseJson: String): String? = kotlin.runCatching {
        jsonPath.parse(responseJson).readString("$.choices[0].message.content")
    }.getOrNull()

    /**
     * @return assistant 返回的 content 文本
     * @throws NoStackTraceException 未配置 / 服务端未返回 content
     */
    suspend fun chatJson(systemPrompt: String, userPrompt: String): String {
        if (!isConfigured()) {
            throw NoStackTraceException("AI 服务未配置")
        }
        val body = GSON.toJson(
            mapOf(
                "model" to AppConfig.aiModel,
                "temperature" to 0.0,
                "response_format" to mapOf("type" to "json_object"),
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                )
            )
        )
        val response = okHttpClient.newCallStrResponse {
            try {
                url(endpointOf(AppConfig.aiBaseUrl))
            } catch (e: IllegalArgumentException) {
                throw NoStackTraceException("AI 服务地址异常: ${AppConfig.aiBaseUrl}")
            }
            AppConfig.aiApiKey.takeIf { it.isNotBlank() }?.let {
                addHeader("Authorization", "Bearer $it")
            }
            postJson(body)
        }
        val text = response.body ?: throw NoStackTraceException("AI 服务无响应体")
        return extractContent(text) ?: throw NoStackTraceException("AI 服务返回异常: ${text.take(200)}")
    }

    suspend fun testConnection(): Result<String> = kotlin.runCatching {
        chatJson("You reply with JSON only.", """回复 {"ok":true}""")
    }.onFailure {
        coroutineContext.ensureActive()
    }
}
