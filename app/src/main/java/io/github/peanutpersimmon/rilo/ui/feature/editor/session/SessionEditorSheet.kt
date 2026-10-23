package io.github.peanutpersimmon.rilo.ui.feature.editor.session

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rilo.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.*
import java.time.LocalDateTime

@Composable
fun SessionEditorSheet(
    viewModel: SessionEditorViewModel,
    id: Long?,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    isActive: Boolean = false,
    sourceEventId: Long? = null,
    voiceSeed: io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed? = null,
    onDeleted: (DeletedSessionSnapshot, suspend (DeletedSessionSnapshot) -> Boolean) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberPickerHostState()
    val maxRecordDateTime = LocalDateTime.now().withSecond(0).withNano(0)
    var ready by remember(id, sourceEventId) { mutableStateOf(false) }
    LaunchedEffect(id, sourceEventId, voiceSeed) { viewModel.load(id, sourceEventId).join(); if (!viewModel.uiState.value.loading) voiceSeed?.let(viewModel::applyVoice); ready = true }
    LaunchedEffect(ready, state.errorMessage) {
        if (ready) state.errorMessage?.let { message ->
            viewModel.clearError()
            snackbar.showSnackbar(message)
        }
    }
    EditorLayout(
        if (id == null) "新建记录" else "编辑记录",
        ready && (if (state.mode == EditorMode.CREATE) state.canSavePast else state.canSaveEdit),
        snackbar, onBack, { viewModel.save(onSuccess = onBack) },
        deleteEnabled = ready && !state.saving,
        onDelete = if (id != null) ({
            viewModel.delete { snapshot -> onDeleted(snapshot, viewModel::restore) }
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
        state.sourceEventTitle?.let {
            androidx.compose.material3.Text("来自日程 · $it", style = io.github.peanutpersimmon.rilo.ui.theme.AppTheme.type.caption)
        }
        EditorTimeModule {
            SplitDateTimeField(
                if (state.endDate != null || state.isActive) "开始时间" else "发生时间",
                EditorTimeRole.START, state.startDate,
                state.startTime.takeIf { state.startTimeConfirmed }, picker,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                onDateChange = { date -> if (date != null) viewModel.update { it.copy(startDate = date) } },
                onTimeChange = { time -> if (time != null) viewModel.update { it.copy(startTime = time, startTimeConfirmed = true) } },
                maxDateTime = maxRecordDateTime)
            if (!state.startTimeConfirmed) androidx.compose.material3.Text(
                "请选择具体发生时间",
                style = io.github.peanutpersimmon.rilo.ui.theme.AppTheme.type.caption,
            )
            SplitDateTimeField("结束时间", EditorTimeRole.END, state.endDate, state.endTime, picker,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                onClear = if (!state.isActive) viewModel::clearEnd else null,
                maxDateTime = maxRecordDateTime,
                onDateTimeChange = viewModel::setEnd)
        }
        EditorToolGroup {
            RecordCategoryPicker(state.options.categories, state.categoryId, state.kind) { category, kind ->
                viewModel.chooseCategory(category, kind)
            }
            LocationField(state.location) { value -> viewModel.update { it.copy(location = value) } }
            if (state.mode == EditorMode.CREATE) EditorTool(
                Icons.Outlined.PlayArrow, "现在开始", onClick = { viewModel.startNow(onStarted) }, enabled = state.canStartNow,
            )
            else if (isActive) EditorTool(Icons.Outlined.Timer, "计时器", onClick = onStarted)
        }
    }
    PickerHost(picker)
}
