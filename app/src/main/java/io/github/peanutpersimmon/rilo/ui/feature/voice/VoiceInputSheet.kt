package io.github.peanutpersimmon.rilo.ui.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.presentation.voice.*
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rilo.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.ChoiceRow
import io.github.peanutpersimmon.rilo.ui.feature.editor.components.EditorTimeTool
import io.github.peanutpersimmon.rilo.ui.feature.editor.event.EventEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.editor.session.SessionEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.today.SleepEditorSheet
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId

/** Lightweight input surface hosted immediately above the existing bottom navigation, not a modal wizard. */


private enum class VoiceBackAction {
    HideKeyboard,
    CloseInput,
}

private fun voiceBackAction(
    imeVisible: Boolean,
): VoiceBackAction {
    return if (imeVisible) {
        VoiceBackAction.HideKeyboard
    } else {
        VoiceBackAction.CloseInput
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceInputSheet(viewModel: VoiceViewModel, event: EventEditorViewModel,
    record: SessionEditorViewModel, autoStart: Boolean = false, holdActive: Boolean = false,
    pressCancelled: Boolean = false, startedImmediately: Boolean = false, requestId: Int = 0, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    val focus = remember { FocusRequester() }
    val currentCancelled = rememberUpdatedState(pressCancelled)
    var pendingListening by remember { mutableStateOf(false) }
    var permissionInFlight by remember { mutableStateOf(false) }
    val listening = state.audioPhase in setOf(VoiceAudioPhase.Connecting, VoiceAudioPhase.Recording)
    var value by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    LaunchedEffect(state.text) {
        if (value.text != state.text) value = TextFieldValue(state.text, TextRange(state.text.length))
    }
    fun hideKeyboard() { focusManager.clearFocus(); keyboard?.hide() }
    fun resumeListening() {
        if (pendingListening && !currentCancelled.value &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            pendingListening = false
            hideKeyboard()
            viewModel.startAudio()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionInFlight = false
        if (pendingListening && !currentCancelled.value) {
            if (granted) resumeListening()
            else {
                pendingListening = false
                viewModel.audioPermissionDenied()
            }
        }
    }
    fun startAudio() {
        if (currentCancelled.value || viewModel.isAudioActive() || state.busy) return
        hideKeyboard()
        pendingListening = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            resumeListening()
        else {
            permissionInFlight = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (viewModel.isAudioActive()) {
                    pendingListening = false
                    viewModel.cancelAudio()
                } else if (!permissionInFlight) pendingListening = false
            } else if (event == Lifecycle.Event.ON_START && pendingListening &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                resumeListening()
        }
        lifecycle.addObserver(observer)
        onDispose {
            pendingListening = false
            lifecycle.removeObserver(observer)
            viewModel.cancelAudio()
            keyboard?.hide()
        }
    }
    LaunchedEffect(requestId) {
        if (autoStart && !startedImmediately) startAudio()
        else if (!autoStart) {
            pendingListening = false
            focus.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(pressCancelled) {
        if (pressCancelled) {
            pendingListening = false
            viewModel.cancelAudio()
        }
    }
    LaunchedEffect(holdActive, autoStart) {
        if (autoStart && !holdActive && viewModel.isAudioActive()) viewModel.releaseAudio()
    }
    BackHandler(enabled = state.editor == null) {
        if (viewModel.isAudioActive()) viewModel.cancelAudio()
        else when (voiceBackAction(imeVisible)) {
            VoiceBackAction.HideKeyboard -> hideKeyboard()
            VoiceBackAction.CloseInput -> if (!state.busy) onDismiss()
        }
    }
    state.editor?.let { editor ->
        LaunchedEffect(editor) { pendingListening = false; viewModel.cancelAudio(); hideKeyboard() }
        if (editor.sleep) {
            SleepEditorSheet(record, editor.id, onDismiss, voiceSeed = editor.seed)
            return@let
        }
        when (editor.type) {
            VoiceType.Event -> EventEditorSheet(event, editor.id, onDismiss, voiceSeed = editor.seed, onDeleted = { _, _ -> onDismiss() })
            VoiceType.Record -> SessionEditorSheet(record, editor.id, onDismiss, onDismiss, voiceSeed = editor.seed, onDeleted = { _, _ -> onDismiss() })
        }
    }
    val picker = rememberPickerHostState()
    Surface(Modifier.fillMaxWidth().padding(horizontal = AppTheme.dimens.pageHorizontal),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, tonalElevation = 0.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(value, { changed ->
                    value = changed
                    if (changed.text != state.text) viewModel.editText(changed.text)
                }, Modifier.weight(1f).focusProperties { canFocus = !listening && !state.busy }.focusRequester(focus).testTag("quick_input_text"),
                    readOnly = state.busy || listening, maxLines = 4, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!listening && !state.busy && state.text.isNotBlank()) { hideKeyboard(); viewModel.submit() } }),
                    decorationBox = { inner -> Box { if (state.text.isEmpty()) Text(when {
                        listening -> "正在聆听…"
                        state.audioPhase in setOf(VoiceAudioPhase.Uploading, VoiceAudioPhase.WaitingForTool, VoiceAudioPhase.Executing) -> "正在处理语音…"
                        else -> "快速输入…"
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                IconButton(onClick = { if (listening) viewModel.releaseAudio() else startAudio() },
                    enabled = listening || !state.busy) {
                    Icon(if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, if (listening) "停止聆听" else "开始聆听")
                }
                IconButton(onClick = { hideKeyboard(); viewModel.submit() }, enabled = state.text.isNotBlank() && !state.busy && !listening,
                    modifier = Modifier.testTag("quick_input_execute")) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.ArrowUpward, "执行输入")
                }
                IconButton(onClick = { viewModel.cancelAudio(); onDismiss() },
                    enabled = !state.busy || viewModel.isAudioActive(), modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "关闭快速输入", Modifier.size(18.dp)) }
            }
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                if (listening && state.text.isNotEmpty()) Text("● 正在聆听…", style = MaterialTheme.typography.bodySmall)
                if (state.chooseTarget) {
                    Text("你指的是哪一个？")
                    state.targets.forEach { candidate -> ChoiceRow(candidate.label, false) { viewModel.selectTarget(candidate) } }
                }
                val d = state.draft
                when (state.clarification) {
                    VoiceClarification.ObjectTypeAmbiguous -> {
                        Text(d?.clarificationMessage ?: "请给出具体安排日期或时间，才能创建日程。")
                        d?.clarificationOptions?.forEachIndexed { index, option ->
                            val fields = option.draft.fields
                            val title = fields.title.applyTo(null) ?: option.draft.targetText
                            val summary = when (option.type) {
                                VoiceType.Event -> "创建日程：$title · ${fields.date.applyTo(null)?.toString().orEmpty()} ${fields.start.applyTo(null)?.toString().orEmpty()}${fields.end.applyTo(null)?.let { "–$it" }.orEmpty()}"
                                VoiceType.Record -> "创建记录：$title"
                            }
                            ChoiceRow(summary, false) { viewModel.chooseClarificationOption(index) }
                        }
                    }
                    VoiceClarification.RecordMissingTime -> Text("${d?.fields?.date?.applyTo(null)?.let { "$it " }.orEmpty()}具体几点发生？")
                    VoiceClarification.EventMissingTime -> Text("请补充日程日期或开始时刻。")
                    VoiceClarification.ReminderOffsetUnresolved -> Text("要提前多久提醒？")
                    VoiceClarification.SleepMissingStart -> Text("请补充睡眠开始时间。")
                    VoiceClarification.SleepMissingEnd -> Text("请补充睡眠结束时间。")
                    VoiceClarification.SleepKindUnresolved -> Text("请在睡眠编辑器确认午睡或夜间睡眠。")
                    VoiceClarification.NoActiveSleep -> Text("没有正在进行的睡眠；不会结束普通记录。")
                    VoiceClarification.InvalidFutureFact -> Text("实际记录不能发生在未来，请确认时间。")
                    VoiceClarification.InvalidTimeRange -> Text("结束时间必须晚于开始时间。")
                    VoiceClarification.UnsupportedResponsibility -> Text("RILO 不支持待办或截止任务。请说明具体日程安排。")
                    VoiceClarification.TargetNotFound -> Text("没有找到对应内容，请重新指定。")
                    VoiceClarification.CategoryUnresolved -> Text(state.error ?: "请确认要使用的分类名称。")
                    VoiceClarification.IntervalMissingBoundary -> Text("请补充这段时间的开始和结束时刻。")
                    VoiceClarification.TargetNotUnique -> Text("找到多个符合的内容，请选择其中一个。")
                    VoiceClarification.MultiplePrimaryObjects -> Text("一次只能处理一个主要对象，请先选择其中一项重新输入。")
                    VoiceClarification.TimeSemanticConflict -> Text(d?.clarificationMessage ?: "对象与时间含义冲突，请确认实际时间或重新输入。")
                    VoiceClarification.SourceEventMissing -> {
                        Text("没有找到对应日程。")
                        TextButton(onClick = viewModel::createRecordWithoutEvent) { Text("创建普通记录（不关联来源日程）") }
                    }
                    VoiceClarification.ManualTypeChange -> Unit
                    VoiceClarification.UnsupportedTag -> Text("RILO 当前仅支持分类。请指定分类，或去掉标签要求后重试。")
                    null -> Unit
                }
                state.typeChangeWarning?.let { warning ->
                    Text(warning)
                    Row {
                        TextButton(onClick = viewModel::confirmTypeChange) { Text("确认改类型") }
                        TextButton(onClick = viewModel::cancelTypeChange) { Text("返回") }
                    }
                }
                if (d != null) state.missing.forEach { field ->
                    val dateField = field in setOf(VoiceMissingField.Date, VoiceMissingField.EndDate)
                    val label = when (field) {
                        VoiceMissingField.Date -> "选择日期"; VoiceMissingField.EndDate -> "结束日期"
                        VoiceMissingField.Start -> "确认开始时间"; VoiceMissingField.End -> "确认结束时间"
                    }
                    EditorTimeTool(if (dateField) Icons.Outlined.CalendarMonth else Icons.Outlined.Schedule, label, label, {
                        if (dateField) {
                            val day = when (field) {
                                VoiceMissingField.EndDate -> d.fields.endDate.applyTo(null)
                                else -> d.fields.date.applyTo(null)
                            } ?: Instant.ofEpochMilli(d.nowMillis)
                                .atZone(io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId.current)
                                .toLocalDate()
                            picker.show(day) { chosen -> if (chosen != null) viewModel.setDate(field, chosen) }
                        } else {
                            val time = when (field) {
                                VoiceMissingField.End -> d.fields.end.applyTo(null)
                                else -> d.fields.start.applyTo(null)
                            }
                            picker.show(time) { chosen -> if (chosen != null) viewModel.setTime(field, chosen) }
                        }
                    })
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (state.futureRecord) TextButton(onClick = { viewModel.chooseType(VoiceType.Event) }) { Text("手动改为日程") }
                if (state.error != null && d != null && !state.chooseTarget) {
                    Row {
                        if (d.action in setOf(VoiceAction.CreateEvent, VoiceAction.CreateRecord,
                                VoiceAction.StartRecord, VoiceAction.CreateSleepRecord, VoiceAction.StartSleep) || state.selected != null &&
                            d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord, VoiceAction.CreateRecordFromEvent))
                            TextButton(onClick = viewModel::openEditor) { Text("在编辑器中修改") }
                        if (state.targets.isEmpty() && d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord))
                            TextButton(onClick = viewModel::createInstead) { Text("新建") }
                    }
                }
            }
        }
    }
    PickerHost(picker)
}
