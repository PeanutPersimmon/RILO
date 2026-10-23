package io.github.peanutpersimmon.rilo.ui.feature.categories

import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey

internal data class CategoryColorDraft(
    val originalKey: String,
    val selectedKey: String = originalKey,
) {
    fun select(key: CategoryColorKey): CategoryColorDraft = copy(selectedKey = key.storageKey)
}
