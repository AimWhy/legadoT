package io.legado.app

import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.entities.HttpTTS
import io.legado.app.model.readaloud.HttpTtsPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpTtsTest {

    @Test
    fun voiceRuleReachesFakeTtsServiceAndReturnsAudio() = runBlocking {
        val audio = byteArrayOf(1, 2, 3, 4)
        var receivedVoice: String? = null
        val server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                receivedVoice = session.parameters["voice"]?.firstOrNull()
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "audio/mpeg",
                    ByteArrayInputStream(audio),
                    audio.size.toLong()
                )
            }
        }
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        try {
            val tts = HttpTTS(
                id = 1,
                name = "fake",
                url = "http://127.0.0.1:${server.listeningPort}/audio?voice={{speakVoice}}"
            )
            assertArrayEquals(audio, HttpTtsPreview.fetch(tts, "测试", 10, "voice-a"))
            assertEquals("voice-a", receivedVoice)
        } finally {
            server.stop()
        }
    }
}
