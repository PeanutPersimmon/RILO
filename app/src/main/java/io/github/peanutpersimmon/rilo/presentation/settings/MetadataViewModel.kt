package io.github.peanutpersimmon.rilo.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.repository.CategoryUsage
import io.github.peanutpersimmon.rilo.domain.usecase.CategoryUseCases
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MetadataUiState(
    val categories: List<Category> = emptyList(),
    val categoryKeywords: Map<Long, List<KeywordRule>> = emptyMap(),
    val error: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataViewModel(
    private val categories: CategoryUseCases,
    private val getRecentLocations: io.github.peanutpersimmon.rilo.domain.usecase.GetRecentLocationsUseCase? = null,
) : ViewModel() {
    private val _recentLocations = MutableStateFlow<List<String>>(emptyList())
    val recentLocations = _recentLocations.asStateFlow()
    fun refreshRecentLocations() = viewModelScope.launch {
        try { _recentLocations.value = getRecentLocations?.invoke().orEmpty() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _recentLocations.value = emptyList() }
    }
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val keywordObservationActive = MutableStateFlow(false)
    private val categoryKeywords = keywordObservationActive
        .flatMapLatest { active ->
            if (active) categories.observeKeywordsByCategory() else flowOf(emptyMap())
        }
    val uiState = combine(
        categories.observe(),
        categoryKeywords,
        error,
        message,
    ) { c, keywords, e, message ->
        MetadataUiState(c, keywords, e, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetadataUiState())

    fun setKeywordObservationActive(active: Boolean) {
        keywordObservationActive.value = active
    }

    fun createCategory(
        name: String,
        colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
        onDone: (Long) -> Unit,
    ) = runOperation { onDone(categories.create(name, colorKey, keywords)) }
    fun updateCategory(id: Long, name: String, colorKey: String, sortOrder: Int, onDone: () -> Unit = {}) =
        runOperation { categories.update(id, name, colorKey, sortOrder); onDone() }
    fun getCategoryUsage(id: Long, onDone: (CategoryUsage) -> Unit) =
        runOperation { onDone(categories.usage(id)) }
    fun deleteCategory(id: Long, onDone: () -> Unit = {}) =
        runOperation { categories.delete(id); onDone() }
    fun reorderCategories(ids: List<Long>) = runOperation { categories.reorder(ids) }
    fun addCategoryKeyword(categoryId: Long, keyword: String, priority: Int = 0, onDone: () -> Unit = {}) =
        runOperation { categories.addKeyword(categoryId, keyword, priority); onDone() }
    fun addCategoryKeywords(
        categoryId: Long,
        keywords: List<String>,
        priority: Int = 0,
        onDone: () -> Unit = {},
    ) = runOperation {
        categories.addKeywords(categoryId, keywords, priority)
        onDone()
    }
    fun updateCategoryKeyword(
        categoryId: Long,
        id: Long,
        keyword: String,
        priority: Int,
        onDone: () -> Unit = {},
    ) = runOperation { categories.updateKeyword(categoryId, id, keyword, priority); onDone() }
    fun deleteCategoryKeyword(categoryId: Long, id: Long, onDone: () -> Unit = {}) =
        runOperation { categories.deleteKeyword(categoryId, id); onDone() }
    fun clearError() { error.value = null; message.value = null }
    private fun runOperation(action: suspend () -> Unit) = viewModelScope.launch {
        error.value = null
        message.value = null
        try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
    }
    companion object {
        fun factory(categories: CategoryUseCases,
            recentLocations: io.github.peanutpersimmon.rilo.domain.usecase.GetRecentLocationsUseCase? = null) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MetadataViewModel(categories, recentLocations) as T
            }
    }
}
