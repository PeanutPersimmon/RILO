package io.github.peanutpersimmon.rilo.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Insert
    suspend fun insert(rule: KeywordRuleEntity): Long

    @Update
    suspend fun update(rule: KeywordRuleEntity)

    @Delete
    suspend fun delete(rule: KeywordRuleEntity)

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getById(id: Long): KeywordRuleEntity?

    @Query("""
        SELECT keyword_rules.* FROM keyword_rules
        JOIN categories ON categories.id = keyword_rules.categoryId
        WHERE keyword_rules.isEnabled = 1
        ORDER BY keyword_rules.priority DESC,
                 length(keyword_rules.keyword) DESC,
                 categories.sortOrder ASC, keyword_rules.id ASC
    """)
    suspend fun getEnabledCategoryRules(): List<KeywordRuleEntity>

    @Query("SELECT * FROM keyword_rules WHERE categoryId = :categoryId ORDER BY priority DESC, length(keyword) DESC, id ASC")
    fun observeCategoryRules(categoryId: Long): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules ORDER BY id ASC")
    suspend fun getAll(): List<KeywordRuleEntity>

    @Query("DELETE FROM keyword_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long): Int

}

