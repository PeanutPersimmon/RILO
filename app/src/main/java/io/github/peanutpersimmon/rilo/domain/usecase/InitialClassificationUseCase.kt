package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.service.KeywordRuleResolver
import io.github.peanutpersimmon.rilo.domain.service.ResolvedClassification

data class InitialClassificationInput(
    val title: String,
    val explicitCategoryId: Long? = null,
    val categoryWasExplicitlySet: Boolean = false,
    val applyKeywordRules: Boolean,
)

class InitialClassificationUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: InitialClassificationInput): ResolvedClassification {
        val automatic = if (input.applyKeywordRules) {
            val categorySortOrderById = repository.getCategories()
                .associate { it.id to it.sortOrder }
            KeywordRuleResolver.resolveInitialClassification(
                input.title,
                repository.getEnabledKeywordRules(),
                categorySortOrderById,
            )
        } else {
            ResolvedClassification(categoryId = null)
        }
        return ResolvedClassification(
            categoryId = if (input.categoryWasExplicitlySet) {
                input.explicitCategoryId
            } else {
                automatic.categoryId
            },
        )
    }
}

