package io.legado.app.help.http

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class HttpLogDecompressionTest {

    @Test
    fun `http logger wraps decompression interceptor`() {
        val source = File("src/main/java/io/legado/app/help/http/HttpHelper.kt").readText()
        val logger = source.indexOf("builder.addInterceptor(HttpLogInterceptor)")
        val decompressor = source.indexOf("builder.addInterceptor(DecompressInterceptor)")

        assertTrue(logger >= 0)
        assertTrue(decompressor > logger)
    }

    @Test
    fun `outer observer sees decompressed gzip response`() {
        assertDecompressed(
            encoding = "gzip",
            fixture = "H4sIAAAAAAAACqtWyk0tLk5MT1WyUkrOzy0oSi0uTk1RKEotLsjPK05VqgUARlFAECEAAAA=",
        )
    }

    @Test
    fun `outer observer sees decompressed deflate response`() {
        assertDecompressed(
            encoding = "deflate",
            fixture = "q1bKTS0uTkxPVbJSSs7PLShKLS5OTVEoSi0uyM8rTlWqBQA=",
        )
    }

    @Test
    fun `outer observer sees decompressed brotli response`() {
        assertDecompressed(
            encoding = "br",
            fixture = "GyAA+I3EOBbxQnUiSpG2vdYZe+vThkhSSS1SAz7qaQbWHtI=",
        )
    }

    private fun assertDecompressed(encoding: String, fixture: String) {
        val expected = "{\"message\":\"compressed response\"}"
        val compressed = Base64.getDecoder().decode(fixture)
        var observedBody: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                observedBody = response.peekBody(4096).string()
                response
            }
            .addInterceptor(DecompressInterceptor)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Encoding", encoding)
                    .header("Content-Length", compressed.size.toString())
                    .body(compressed.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        client.newCall(
            Request.Builder().url("https://example.test/").build()
        ).execute().use { response ->
            assertEquals(expected, observedBody)
            assertEquals(expected, response.body.string())
            assertNull(response.header("Content-Encoding"))
            assertNull(response.header("Content-Length"))
        }
    }
}
