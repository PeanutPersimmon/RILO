package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rilo.domain.service.ResolvedClassification

/** Resolves an AI-provided phrase before the pure planner is invoked. */
object CategoryHintResolver {
    fun resolve(
        categoryHint: String,
        categories: Collection<Category>,
        rules: Collection<KeywordRule>,
    ): ResolvedClassification {
        val hint = CategoryKeywordNormalizer.normalize(categoryHint)
        if (hint.isEmpty()) return ResolvedClassification(null)

        val activeCategories = categories
        val activeById = activeCategories.associateBy(Category::id)
        val exactCategory = activeCategories
            .asSequence()
            .filter { CategoryKeywordNormalizer.normalize(it.name) == hint }
            .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.id })
            .firstOrNull()
        if (exactCategory != null) {
            return ResolvedClassification(exactCategory.id)
        }

        val eligibleRules = rules.filter { rule ->
            rule.isEnabled && rule.categoryId in activeById &&
                CategoryKeywordNormalizer.normalize(rule.keyword).isNotEmpty()
        }
        val exactKeyword = eligibleRules.filter {
            CategoryKeywordNormalizer.normalize(it.keyword) == hint
        }
        val containedKeyword = eligibleRules.filter {
            hint.contains(CategoryKeywordNormalizer.normalize(it.keyword))
        }
        val winner = (exactKeyword.ifEmpty { containedKeyword })
            .minWithOrNull(
                compareByDescending<KeywordRule> { it.priority }
                    .thenByDescending { CategoryKeywordNormalizer.normalize(it.keyword).length }
                    .thenBy { activeById[it.categoryId]?.sortOrder ?: Int.MAX_VALUE }
                    .thenBy { it.id },
            )
        return ResolvedClassification(
            categoryId = winner?.categoryId,
        )
    }
}
