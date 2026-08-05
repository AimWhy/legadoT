package io.legado.app.help.http

import okhttp3.Headers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpLogInterceptorTest {

    @Test
    fun `authorization headers are redacted`() {
        val formatted = HttpLogInterceptor.formatHeaders(
            Headers.Builder()
                .add("Authorization", "Bearer secret")
                .add("Proxy-Authorization", "Basic secret")
                .add("Accept", "application/json")
                .build()
        )
        assertFalse(formatted.contains("secret"))
        assertTrue(formatted.contains("Authorization: [REDACTED]"))
        assertTrue(formatted.contains("Accept: application/json"))
    }
}
