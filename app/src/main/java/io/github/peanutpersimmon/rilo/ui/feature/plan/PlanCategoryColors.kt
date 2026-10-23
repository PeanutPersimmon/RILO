package io.github.peanutpersimmon.rilo.ui.feature.plan

import androidx.compose.material3.ColorScheme
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.ui.theme.CategoryColors
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors

/** A missing category stays absent; the stored neutral preset remains a real category color. */
internal fun planCategoryColors(
    categoryId: Long?,
    categoryColorKeys: Map<Long, String>,
    colorScheme: ColorScheme,
): CategoryColors? {
    val key = categoryId?.let(categoryColorKeys::get) ?: return null
    if (CategoryColorKey.fromStorage(key) == null) return null
    return resolveCategoryColors(key, colorScheme)
}
