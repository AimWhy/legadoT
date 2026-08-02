package io.legado.app.help.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiClientTest {

    @Test
    fun `endpoint tolerates trailing slash and an already complete path`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiClient.endpointOf("https://api.deepseek.com/v1")
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiClient.endpointOf("https://api.deepseek.com/v1/")
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiClient.endpointOf("https://api.deepseek.com/v1/chat/completions")
        )
    }

    @Test
    fun `content is pulled out of the first choice`() {
        val json = """
            {"choices":[{"message":{"role":"assistant","content":"{\"roles\":[]}"}}]}
        """.trimIndent()
        assertEquals("""{"roles":[]}""", AiClient.extractContent(json))
    }

    @Test
    fun `missing or malformed choices yield null rather than throwing`() {
        assertNull(AiClient.extractContent("""{"error":{"message":"bad key"}}"""))
        assertNull(AiClient.extractContent("""{"choices":[]}"""))
        assertNull(AiClient.extractContent("not json at all"))
    }
}
