package io.github.peanutpersimmon.rilo.presentation.editor

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {
    @Test fun eventDateOnlyCanSave() = assertTrue(EventEditorUiState(title = "会议").canSave)
    @Test fun sessionCreateWithOnlyOccurrenceTimeCanSavePoint() {
        val state = SessionEditorUiState(title = "记录")
        assertTrue(state.canSavePast)
        assertEquals(SessionTemporalType.POINT, state.saveTemporalType)
    }
    @Test fun sessionCreateWithTitleMayStartNow() = assertTrue(SessionEditorUiState(title = "记录").canStartNow)

    @Test fun editorOptionsKeepArchiveMetadata() {
        val result = editorOptions(
            listOf(Category(3, "旧分类", true, createdAtMillis = 0, updatedAtMillis = 0)),
        )
        assertTrue(result.categories.single().isArchived)
    }

    @Test fun eventAndRecordEditorStatesExposeOnlyCategoryMetadata() {
        assertTrue(EventEditorUiState::class.java.declaredFields.none { it.name == "tagIds" })
        assertTrue(SessionEditorUiState::class.java.declaredFields.none { it.name == "tagIds" })
        assertTrue(EditorOptionsUiModel::class.java.declaredFields.none { it.name == "tags" })
    }
}
