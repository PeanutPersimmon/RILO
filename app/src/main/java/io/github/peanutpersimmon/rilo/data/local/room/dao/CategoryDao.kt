package io.github.peanutpersimmon.rilo.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name, id")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY sortOrder, name, id")
    suspend fun getActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name, id")
    suspend fun getAllIncludingArchived(): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long): Int

}
