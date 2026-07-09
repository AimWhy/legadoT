package io.legado.app.data.entities

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookSourcePartHasJsTest {

    private val partSource =
        File("src/main/java/io/legado/app/data/entities/BookSourcePart.kt").readText()

    @Test
    fun databaseViewExposesHasJs() {
        assertTrue("视图 SQL 应含 hasJs 计算列", partSource.contains("hasJs"))
        assertTrue("hasJs 应按 mainJs 非空白派生", partSource.contains("trim(mainJs)"))
    }
}
