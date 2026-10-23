package io.github.peanutpersimmon.rilo.ui.feature.categories

import androidx.compose.material3.lightColorScheme
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.ui.theme.resolveCategoryColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryColorDraftTest {
    @Test
    fun consecutiveChoicesOnlyChangePreviewUntilConfirmed() {
        val savedKey = CategoryColorKey.BLUE.storageKey
        var draft = CategoryColorDraft(savedKey)
        val previewContainers = mutableListOf<androidx.compose.ui.graphics.Color>()
        listOf(CategoryColorKey.RED, CategoryColorKey.PURPLE, CategoryColorKey.AMBER).forEach { key ->
            draft = draft.select(key)
            assertEquals(key.storageKey, draft.selectedKey)
            assertEquals(savedKey, draft.originalKey)
            previewContainers += resolveCategoryColors(draft.selectedKey, lightColorScheme()).container
        }
        assertEquals(CategoryColorKey.AMBER.storageKey, draft.selectedKey)
        assertEquals(3, previewContainers.toSet().size)
    }

    @Test
    fun dismissingDraftLeavesPersistedKeyUntouchedAndReopenStartsFresh() {
        val savedKey = CategoryColorKey.GREEN.storageKey
        val dismissedDraft = CategoryColorDraft(savedKey).select(CategoryColorKey.PINK)
        assertEquals(savedKey, dismissedDraft.originalKey)
        assertEquals(savedKey, CategoryColorDraft(savedKey).selectedKey)
    }

    @Test
    fun newCategoryStartsNeutralAndUsesFinalChoiceOnConfirmation() {
        val draft = CategoryColorDraft(CategoryColorKey.NEUTRAL.storageKey)
            .select(CategoryColorKey.BLUE)
            .select(CategoryColorKey.RED)
        assertEquals(CategoryColorKey.RED.storageKey, draft.selectedKey)
    }
}
