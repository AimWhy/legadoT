package io.legado.app.model.readaloud

import io.legado.app.data.entities.HttpTTS
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fetches one preview audio response without touching the chapter audio cache. */
object HttpTtsPreview {

    suspend fun fetch(
        tts: HttpTTS,
        text: String,
        speechRate: Int,
        voice: String?
    ): ByteArray {
        val analyzeUrl = AnalyzeUrl(
            tts.url,
            speakText = text,
            speakSpeed = speechRate,
            speakVoice = voice,
            source = tts,
            readTimeout = 30_000L,
            coroutineContext = currentCoroutineContext()
        )
        var response = analyzeUrl.getResponseAwait()
        currentCoroutineContext().ensureActive()
        val loginCheckJs = tts.loginCheckJs
        if (loginCheckJs?.isNotBlank() == true) {
            response = analyzeUrl.evalJS(loginCheckJs, response) as okhttp3.Response
        }
        val contentType = response.headers["Content-Type"]?.substringBefore(';').orEmpty()
        if (contentType == "application/json" || contentType.startsWith("text/")) {
            throw NoStackTraceException(response.body.string().take(200))
        }
        val bytes = response.body.bytes()
        if (bytes.isEmpty()) throw NoStackTraceException("TTS 返回空音频")
        return bytes
    }
}
