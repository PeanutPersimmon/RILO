package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class CategoryUseCases(private val repository: CategoryRepository) {
    fun observe() = repository.observeCategories()
    suspend fun create(
        name: String,
        colorKey: String = io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
    ) = repository.createCategory(name, colorKey, keywords)
    suspend fun update(id: Long, name: String, colorKey: String, sortOrder: Int) =
        repository.updateCategory(id, name, colorKey, sortOrder)
    suspend fun usage(id: Long) = repository.getCategoryUsage(id)
    suspend fun delete(id: Long) = repository.deleteCategory(id)
    suspend fun reorder(ids: List<Long>) = repository.reorderCategories(ids)
    fun observeKeywords(categoryId: Long) = repository.observeCategoryKeywords(categoryId)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeKeywordsByCategory() = observe().flatMapLatest { categories ->
        if (categories.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(categories.map { category ->
                observeKeywords(category.id).map { category.id to it }
            }) { entries -> entries.toMap() }
        }
    }
    suspend fun addKeyword(categoryId: Long, keyword: String, priority: Int = 0) =
        repository.addCategoryKeyword(categoryId, keyword, priority)
    suspend fun addKeywords(categoryId: Long, keywords: List<String>, priority: Int = 0) =
        repository.addCategoryKeywords(categoryId, keywords, priority)
    suspend fun updateKeyword(categoryId: Long, id: Long, keyword: String, priority: Int) =
        repository.updateCategoryKeyword(categoryId, id, keyword, priority)
    suspend fun deleteKeyword(categoryId: Long, id: Long) =
        repository.deleteCategoryKeyword(categoryId, id)
}
