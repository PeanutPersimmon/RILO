package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rilo.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rilo.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.DateTimeField
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorLayout
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorTimeRole
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.LocalDateTime

@Composable
fun SleepEditorSheet(
    viewModel: SessionEditorViewModel,
    id: Long?,
    onDismiss: () -> Unit,
    voiceSeed: VoiceEditorSeed? = null,
    warning: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var ready by remember(id) { mutableStateOf(false) }
    var startConfirmed by remember(id, voiceSeed) {
        mutableStateOf(voiceSeed == null || voiceSeed.draft.fields.startAt != null)
    }
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberPickerHostState()
    val maxRecordDateTime = LocalDateTime.now().withSecond(0).withNano(0)
    LaunchedEffect(id, voiceSeed) {
        if (voiceSeed == null) viewModel.loadSleep(id).join()
        else {
            viewModel.load(id).join()
            viewModel.applySleepVoice(voiceSeed)
        }
        ready = true
    }
    LaunchedEffect(ready, state.errorMessage) {
        if (ready) state.errorMessage?.let { message ->
            viewModel.clearError()
            snackbar.showSnackbar(message)
        }
    }
    val start = LocalDateTime.of(state.startDate, state.startTime)
    val end = state.endDate?.let { date -> state.endTime?.let { LocalDateTime.of(date, it) } }
    val valid = ready && startConfirmed && !state.saving && end != null && end.isAfter(start)
    val save: () -> Unit = {
        if (valid) {
            viewModel.update { it.copy(categoryId = null) }
            viewModel.save(onSuccess = onDismiss)
        }
    }
    EditorLayout(
        if (id == null) "新增睡眠" else "编辑睡眠",
        valid,
        snackbar,
        onDismiss,
        save,
    ) {
        if (!ready) {
            Text("载入中")
            return@EditorLayout
        }
        warning?.let {
            Text(
                it,
                style = AppTheme.type.editorDescription,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
            listOf(SessionKind.NIGHT_SLEEP to "夜间睡眠", SessionKind.NAP to "小憩").forEach { (kind, label) ->
                FilterChip(
                    selected = state.kind == kind,
                    enabled = !state.saving,
                    onClick = { viewModel.chooseSleepKind(kind) },
                    label = { Text(label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            DateTimeField(
                label = "开始",
                role = EditorTimeRole.START,
                date = state.startDate,
                time = state.startTime,
                modifier = Modifier.weight(1f),
                valueStyle = AppTheme.type.fieldValue,
                onClick = {
                    if (!state.saving) {
                        picker.show(state.startDate, state.startTime, false, maxRecordDateTime) { date, time ->
                            if (time != null) {
                                startConfirmed = true
                                viewModel.setSleepStart(date, time)
                            }
                        }
                    }
                },
            )
            DateTimeField(
                label = "结束",
                role = EditorTimeRole.END,
                date = state.endDate,
                time = state.endTime,
                modifier = Modifier.weight(1f),
                valueStyle = AppTheme.type.fieldValue,
                onClick = {
                    if (!state.saving) {
                        picker.show(
                            state.endDate ?: state.startDate,
                            state.endTime ?: state.startTime,
                            false,
                            maxRecordDateTime,
                        ) { date, time ->
                            if (time != null) viewModel.setSleepEnd(date, time)
                        }
                    }
                },
            )
        }
        if (end != null && !end.isAfter(start)) {
            Text(
                "结束时间须晚于开始时间",
                style = AppTheme.type.editorDescription,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            )
        }
    }
    PickerHost(picker)
}
