package io.github.peanutpersimmon.rilo.domain.repository

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import kotlinx.coroutines.flow.Flow

data class CategoryUsage(
    val eventCount: Int,
    val sessionCount: Int,
) {
    val isUsed: Boolean get() = eventCount > 0 || sessionCount > 0
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun createCategory(
        name: String,
        colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
    ): Long
    suspend fun updateCategory(id: Long, name: String, colorKey: String, sortOrder: Int)
    suspend fun getCategoryUsage(id: Long): CategoryUsage
    suspend fun deleteCategory(id: Long)
    suspend fun reorderCategories(ids: List<Long>)
    fun observeCategoryKeywords(categoryId: Long): Flow<List<KeywordRule>>
    suspend fun addCategoryKeyword(categoryId: Long, keyword: String, priority: Int = 0): Long
    suspend fun addCategoryKeywords(categoryId: Long, keywords: List<String>, priority: Int = 0)
    suspend fun updateCategoryKeyword(categoryId: Long, id: Long, keyword: String, priority: Int)
    suspend fun deleteCategoryKeyword(categoryId: Long, id: Long)
}
