package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.ChapterRoleScript

@Dao
interface ChapterRoleScriptDao {

    @Query("select * from chapterRoleScripts where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    fun get(bookUrl: String, chapterIndex: Int): ChapterRoleScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg script: ChapterRoleScript)

    @Query("delete from chapterRoleScripts where bookUrl = :bookUrl")
    fun deleteByBook(bookUrl: String)

    @Query("delete from chapterRoleScripts where bookUrl = :bookUrl and chapterIndex = :chapterIndex")
    fun delete(bookUrl: String, chapterIndex: Int)

    @Query("delete from chapterRoleScripts where bookUrl not in (select bookUrl from books)")
    fun deleteOrphans()
}
