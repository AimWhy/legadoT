package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSubDao {

    @get:Query("select * from ruleSubs order by customOrder")
    val all: List<RuleSub>

    @Query("select * from ruleSubs order by customOrder")
    fun flowAll(): Flow<List<RuleSub>>

    @get:Query("select ifnull(max(customOrder), 0) from ruleSubs")
    val maxOrder: Int

    @Query("select * from ruleSubs where url = :url")
    fun findByUrl(url: String): RuleSub?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg ruleSub: RuleSub)

    @Delete
    fun delete(vararg ruleSub: RuleSub)

    @Update
    fun update(vararg ruleSub: RuleSub)
}
