package io.github.peanutpersimmon.rilo.ui.feature.editor.event

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rilo.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.*

@Composable
fun EventEditorSheet(
    viewModel: EventEditorViewModel,
    id: Long?,
    onBack: () -> Unit,
    onCreateRecord: (Long) -> Unit = {},
    onChanged: () -> Unit = {},
    voiceSeed: io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed? = null,
    onDeleted: (DeletedEventSnapshot, suspend (DeletedEventSnapshot) -> Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberPickerHostState()
    var ready by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, voiceSeed) { viewModel.load(id).join(); if (!viewModel.uiState.value.loading) voiceSeed?.let(viewModel::applyVoice); ready = true }
    LaunchedEffect(ready, state.errorMessage) {
        if (ready) state.errorMessage?.let { message ->
            viewModel.clearError()
            snackbar.showSnackbar(message)
        }
    }
    EditorLayout(
        if (id == null) "新建日程" else "编辑日程", ready && state.canSave, snackbar,
        onBack, { viewModel.save { onChanged(); onBack() } },
        deleteEnabled = ready && !state.saving,
        extraActions = {
            if (id != null && id > 0 && state.id == id && ready) EditorIconAction(Icons.AutoMirrored.Outlined.Send, "从日程新建记录",
                { onCreateRecord(id) }, !state.saving)
        },
        onDelete = if (id != null) ({
            viewModel.delete { snapshot ->
                onChanged()
                onDeleted(snapshot) { deleted ->
                    viewModel.restore(deleted).also { restored -> if (restored) onChanged() }
                }
            }
        }) else null,
    ) {
        if (!ready) { androidx.compose.material3.Text("载入中"); return@EditorLayout }
        voiceSeed?.let {
            androidx.compose.material3.Text("请核对后保存 · ${it.draft.originalText}", style = io.github.peanutpersimmon.rilo.ui.theme.AppTheme.type.caption)
        }
        androidx.compose.foundation.layout.Column {
            TitleField(state.title, viewModel::changeTitle)
            NoteField(state.note) { value -> viewModel.update { it.copy(note = value) } }
        }
        EditorTimeModule {
            SplitDateTimeField("日程开始", EditorTimeRole.START, state.date, state.startTime, picker,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                trailingContent = if (reminderBellVisible(state.date, state.startTime)) ({
                    ReminderBell("开始提醒", state.startReminderOffsetMinutes) { offset ->
                        viewModel.update { it.copy(startReminderOffsetMinutes = offset) }
                    }
                }) else null,
                onDateChange = { date -> if (date != null) viewModel.update { it.copy(date = date) } },
                onTimeChange = { time -> viewModel.update { it.copy(startTime = time, startReminderOffsetMinutes = it.startReminderOffsetMinutes.takeIf { time != null }, endTime = it.endTime.takeIf { time != null }, endDate = it.endDate.takeIf { time != null }) } },
                onClear = viewModel::clearStart)
            SplitDateTimeField("日程结束", EditorTimeRole.END, state.endDate, state.endTime, picker,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                onDateChange = { date -> viewModel.update { it.copy(endDate = date) } },
                onTimeChange = { time -> viewModel.update { it.copy(endTime = time, endDate = (it.endDate ?: it.date).takeIf { time != null }) } },
                onClear = { viewModel.update { it.copy(endDate = null, endTime = null) } })
        }
        EditorToolGroup {
            CategoryPicker(state.options.categories, state.categoryId, viewModel::chooseCategory)
            LocationField(state.location) { value -> viewModel.update { it.copy(location = value) } }
        }
    }
    PickerHost(picker)
}

