package io.github.peanutpersimmon.rilo.presentation.editor.event

import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.EditorOptionsUiModel
import java.time.LocalDate
import java.time.LocalTime

data class EventEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.CREATE,
    val id: Long? = null,
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime? = null,
    val startReminderOffsetMinutes: Int? = null,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val categoryId: Long? = null,
    val location: String = "",
    val note: String = "",
    val options: EditorOptionsUiModel = EditorOptionsUiModel(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val eventEndComplete: Boolean get() = endDate == null || endTime != null
    val canSave: Boolean get() = title.isNotBlank() && eventEndComplete && !saving
}
