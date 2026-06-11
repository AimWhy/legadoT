package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.ExploreContainer
import kotlinx.coroutines.flow.Flow

@Dao
interface ExploreContainerDao {

    @get:Query("SELECT * FROM exploreContainers ORDER BY sortOrder ASC")
    val all: List<ExploreContainer>

    @Query("SELECT * FROM exploreContainers ORDER BY sortOrder ASC")
    fun flowAll(): Flow<List<ExploreContainer>>

    @Query("SELECT * FROM exploreContainers WHERE enabled = 1 ORDER BY sortOrder ASC")
    fun flowEnabled(): Flow<List<ExploreContainer>>

    @Query("SELECT * FROM exploreContainers WHERE id = :id")
    fun getById(id: Long): ExploreContainer?

    @get:Query("SELECT ifnull(min(sortOrder), 0) FROM exploreContainers")
    val minOrder: Int

    @get:Query("SELECT ifnull(max(sortOrder), 0) FROM exploreContainers")
    val maxOrder: Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg container: ExploreContainer): List<Long>

    @Update
    fun update(vararg container: ExploreContainer)

    @Delete
    fun delete(vararg container: ExploreContainer)
}
