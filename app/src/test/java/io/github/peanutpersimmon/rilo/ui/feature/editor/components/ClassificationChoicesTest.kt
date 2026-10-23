package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import io.github.peanutpersimmon.rilo.presentation.editor.EditorChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationChoicesTest {
    private val active = EditorChoice(1, "工作")
    private val archived = EditorChoice(2, "旧标签", true)

    @Test fun legacyArchivedChoiceIsAvailableForNewSelection() =
        assertEquals(listOf(active, archived), selectableChoices(listOf(active, archived), emptySet()))

    @Test fun selectedArchivedChoiceRemainsVisible() =
        assertTrue(archived in selectableChoices(listOf(active, archived), setOf(2)))

    @Test fun emptySelectionIsValid() =
        assertEquals(listOf(active, archived), selectableChoices(listOf(active, archived), emptySet()))
}
