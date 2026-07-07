package io.legado.app.data.entities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceMainJsTest {

    @Test
    fun isJsSourceDerivedFromMainJs() {
        val source = BookSource(bookSourceUrl = "https://a.com")
        assertFalse(source.isJsSource())
        source.mainJs = ""
        assertFalse(source.isJsSource())
        source.mainJs = "  "
        assertFalse(source.isJsSource())
        source.mainJs = "var source = {}"
        assertTrue(source.isJsSource())
    }

    @Test
    fun equalCoversMainJs() {
        val a = BookSource(bookSourceUrl = "https://a.com", bookSourceName = "a")
        val b = a.copy()
        assertTrue(a.equal(b))
        b.mainJs = "var source = {}"
        assertFalse(a.equal(b))
    }
}
