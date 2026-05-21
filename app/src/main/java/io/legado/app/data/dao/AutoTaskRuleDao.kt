package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.model.AutoTaskRule

@Dao
interface AutoTaskRuleDao {

    @Query("select * from auto_task_rules")
    fun all(): List<AutoTaskRule>

    @Query("select * from auto_task_rules where id = :id")
    fun getById(id: String): AutoTaskRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg rule: AutoTaskRule)

    @Update
    fun update(vararg rule: AutoTaskRule)

    @Query("delete from auto_task_rules where id = :id")
    fun delete(id: String)

    @Query("delete from auto_task_rules")
    fun deleteAll()
}
