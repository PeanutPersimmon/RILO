package io.github.peanutpersimmon.rilo.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.data.audio.AndroidVoiceAudioRecorder
import io.github.peanutpersimmon.rilo.data.audio.VoiceAudioRecorder
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.domain.voice.agent.*
import io.github.peanutpersimmon.rilo.presentation.editor.userMessage
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class VoiceInputPhase {
    Idle,
    EditingText,
    Listening,
    ResolvingAmbiguity,
    Executing,
    Error,
}
enum class VoiceAudioPhase { Idle, Connecting, Recording, Uploading, WaitingForTool, Executing, Failed }
data class VoiceEditorRequest(
    val type: VoiceType,
    val id: Long?,
    val seed: VoiceEditorSeed,
    val sleep: Boolean = false,
)
data class VoiceUiState(
    val text: String = "", val phase: VoiceInputPhase = VoiceInputPhase.Idle,
    val draft: VoiceActionDraft? = null,
    val targets: List<VoiceTargetCandidate> = emptyList(),
    val selected: VoiceTargetCandidate? = null,
    val chooseTarget: Boolean = false,
    val missing: Set<VoiceMissingField> = emptySet(), val error: String? = null,
    val audioPhase: VoiceAudioPhase = VoiceAudioPhase.Idle,
    val futureRecord: Boolean = false, val editor: VoiceEditorRequest? = null,
    val clarification: VoiceClarification? = null,
    val pendingTypeChange: VoiceActionDraft? = null, val typeChangeWarning: String? = null,
) { val busy: Boolean get() = phase == VoiceInputPhase.Executing ||
    audioPhase in setOf(VoiceAudioPhase.Uploading, VoiceAudioPhase.WaitingForTool, VoiceAudioPhase.Executing) }

class VoiceViewModel(private val repository: RidoCoreRepository,
    private val agent: VoiceAgentEngine? = null,
    private val recorderFactory: () -> VoiceAudioRecorder = ::AndroidVoiceAudioRecorder,
    zoneId: ZoneId? = null) : ViewModel() {
    private val state = MutableStateFlow(VoiceUiState())
    val uiState = state.asStateFlow()
    private val receipts = Channel<VoiceExecutionReceipt>(Channel.BUFFERED)
    val executed = receipts.receiveAsFlow()
    private val fixedZoneId = zoneId
    private val zone: ZoneId get() = fixedZoneId ?: RuntimeZoneId.current
    private val working = java.util.concurrent.atomic.AtomicBoolean(false)
    private var executionAttempt = VoiceExecutionAttempt()
    private var pendingAgentCall: VoiceAgentPendingCall? = null
    private var submissionId: String? = null
    private class AudioSubmission(
        val id: String, val recorder: VoiceAudioRecorder, val turn: QwenAudioTurnController,
    ) {
        val started = CompletableDeferred<Unit>()
        var releaseRequested = false
        val releaseHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        var cancelled = false
        var job: Job? = null
    }
    private var audioSubmission: AudioSubmission? = null
    fun isAudioActive(): Boolean = audioSubmission != null

    fun startAudio() {
        val engine = agent ?: run { fail("AI 语音暂不可用，请使用文字输入"); return }
        if (audioSubmission != null || working.get() || state.value.busy) return
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.event("input type = AUDIO")
        VoiceAgentDiagnosticsStore.event("audio format = PCM_S16LE_MONO_16000")
        val turn = QwenAudioTurnController()
        val recorder = recorderFactory()
        val submission = AudioSubmission(UUID.randomUUID().toString(), recorder, turn)
        audioSubmission = submission
        submissionId = submission.id
        state.update { it.copy(phase = VoiceInputPhase.Listening, audioPhase = VoiceAudioPhase.Connecting, error = null) }
        submission.job = viewModelScope.launch {
            try {
                val resultJob = async { engine.executeAudio(turn, VoiceAgentContext(Instant.now(), zone)) }
                recorder.start({ pcm ->
                    val issue = turn.append(pcm)
                    if (issue != null) audioFailure(submission, issue)
                }, { audioFailure(submission, AudioTurnIssue.UploadFailed) })
                submission.started.complete(Unit)
                VoiceAgentDiagnosticsStore.event("audio started = true")
                if (submission.cancelled) return@launch
                if (submission.releaseRequested) releaseAudioRecording(submission)
                else state.update { it.copy(audioPhase = VoiceAudioPhase.Recording) }
                when (val result = resultJob.await()) {
                    is VoiceAgentResult.Action -> {
                        if (submission.cancelled || audioSubmission !== submission) {
                            result.call.cancel()
                            return@launch
                        }
                        recorder.cancel()
                        audioSubmission = null
                        if (!working.compareAndSet(false, true)) { result.call.cancel(); return@launch }
                        try {
                            state.update { it.copy(audioPhase = VoiceAudioPhase.Executing, phase = VoiceInputPhase.Executing) }
                            pendingAgentCall = result.call
                            routeByPolicy(result.draft)
                        } finally { working.set(false) }
                    }
                    is VoiceAgentResult.NoAction -> failAudio(submission, result.message)
                    is VoiceAgentResult.InvalidToolCall -> failAudio(submission, result.message)
                    is VoiceAgentResult.Unavailable -> failAudio(submission,
                        if (turn.failure == AudioTurnIssue.TooLong) "语音输入时间过长"
                        else if (turn.failure == AudioTurnIssue.Empty) "未检测到语音"
                        else "AI 语音暂不可用，请使用文字输入")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failAudio(submission, "AI 语音暂不可用，请使用文字输入")
            } finally {
                submission.started.complete(Unit)
                recorder.cancel()
                if (audioSubmission === submission) audioSubmission = null
                turn.cancel()
            }
        }
    }

    fun releaseAudio() {
        val submission = audioSubmission ?: return
        if (submission.releaseRequested || submission.cancelled) return
        submission.releaseRequested = true
        state.update { it.copy(audioPhase = VoiceAudioPhase.Uploading) }
        viewModelScope.launch {
            submission.started.await()
            if (audioSubmission === submission && !submission.cancelled) releaseAudioRecording(submission)
        }
    }

    private suspend fun releaseAudioRecording(submission: AudioSubmission) {
        if (submission.cancelled || audioSubmission !== submission ||
            !submission.releaseHandled.compareAndSet(false, true)) return
        try {
            submission.recorder.stop()
            if (submission.cancelled || audioSubmission !== submission) return
            val issue = submission.turn.finishRecording()
            VoiceAgentDiagnosticsStore.event("audio bytes captured = ${submission.turn.bytesCaptured}")
            VoiceAgentDiagnosticsStore.event("audio bytes sent = ${submission.turn.bytesSent}")
            if (issue != null) failAudio(submission, when (issue) {
                AudioTurnIssue.Empty -> "未检测到语音"
                AudioTurnIssue.TooLong -> "语音输入时间过长"
                AudioTurnIssue.UploadFailed -> "AI 语音暂不可用，请使用文字输入"
            })
            else state.update { it.copy(audioPhase = VoiceAudioPhase.WaitingForTool) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failAudio(submission, "AI 语音暂不可用，请使用文字输入")
        }
    }

    private fun audioFailure(submission: AudioSubmission, issue: AudioTurnIssue) {
        viewModelScope.launch {
            if (audioSubmission === submission && !submission.cancelled)
                failAudio(submission, if (issue == AudioTurnIssue.TooLong) "语音输入时间过长"
                    else "AI 语音暂不可用，请使用文字输入")
        }
    }

    private fun failAudio(submission: AudioSubmission, message: String) {
        if (audioSubmission !== submission || submission.cancelled) return
        submission.cancelled = true
        submission.recorder.cancel()
        submission.turn.cancel()
        VoiceAgentDiagnosticsStore.event("audio committed = false")
        audioSubmission = null
        submissionId = null
        state.update { it.copy(phase = VoiceInputPhase.Error, audioPhase = VoiceAudioPhase.Failed, error = message) }
        submission.job?.cancel()
    }

    fun audioPermissionDenied() {
        state.update { it.copy(phase = VoiceInputPhase.Error, audioPhase = VoiceAudioPhase.Failed,
            error = "没有麦克风权限") }
    }

    fun cancelAudio() {
        val submission = audioSubmission ?: return
        submission.cancelled = true
        submission.recorder.cancel()
        submission.turn.cancel()
        VoiceAgentDiagnosticsStore.event("audio cancelled")
        audioSubmission = null
        submissionId = null
        submission.job?.cancel()
        state.update { it.copy(audioPhase = VoiceAudioPhase.Idle, phase = VoiceInputPhase.EditingText) }
    }


    fun reset() { if (!working.get()) {
        cancelAudio()
        pendingAgentCall?.cancel(); pendingAgentCall = null; submissionId = null
        executionAttempt = VoiceExecutionAttempt(); state.value = VoiceUiState()
    } }
    fun editText(text: String) { if (!working.get()) {
        if (text != state.value.text) {
            executionAttempt = VoiceExecutionAttempt()
            submissionId = null
            pendingAgentCall?.cancel(); pendingAgentCall = null
        }
        state.value = VoiceUiState(text = text, phase = VoiceInputPhase.EditingText)
    } }
    fun submit() = work {
        if (state.value.text.isBlank()) return@work
        if (submissionId == null) submissionId = UUID.randomUUID().toString()
        val engine = agent
        if (engine == null) {
            VoiceAgentDiagnosticsStore.begin()
            VoiceAgentDiagnosticsStore.validationResult("unavailable")
            fail("AI 暂不可用，请稍后重试或使用 + 手动创建")
            return@work
        }
        when (val result = engine.executeText(state.value.text, VoiceAgentContext(Instant.now(), zone))) {
            is VoiceAgentResult.Action -> {
                pendingAgentCall?.cancel()
                pendingAgentCall = result.call
                routeByPolicy(result.draft)
            }
            is VoiceAgentResult.NoAction -> fail(result.message)
            is VoiceAgentResult.InvalidToolCall -> fail(result.message)
            is VoiceAgentResult.Unavailable -> {
                VoiceAgentDiagnosticsStore.validationResult("unavailable")
                fail("AI 暂不可用，请稍后重试或使用 + 手动创建")
            }
        }
    }
    private suspend fun routeByPolicy(draft: VoiceActionDraft) {
        when (val decision = VoiceIntentPolicy.evaluate(draft, zone)) {
            is VoiceIntentDecision.Ready -> {
                VoiceAgentDiagnosticsStore.policyResult("Ready")
                resolve(decision.draft)
            }
            is VoiceIntentDecision.NeedsClarification -> {
                VoiceAgentDiagnosticsStore.policyResult("NeedsClarification:${decision.reason}")
                VoiceAgentDiagnosticsStore.reason(decision.reason.name)
                val range = VoiceIntentPolicy.temporalRange(decision.draft, zone)
                val needed = when (decision.reason) {
                    VoiceClarification.RecordMissingTime -> setOf(VoiceMissingField.Start)
                    VoiceClarification.EventMissingTime -> if (decision.draft.action != VoiceAction.CreateEvent)
                        emptySet() else if (decision.draft.fields.date.applyTo(null) == null)
                        setOf(VoiceMissingField.Date) else setOf(VoiceMissingField.Start)
                    VoiceClarification.IntervalMissingBoundary -> if (decision.draft.action !in setOf(
                            VoiceAction.CreateEvent, VoiceAction.CreateRecord)) emptySet() else buildSet {
                        if (decision.draft.action == VoiceAction.CreateEvent &&
                            decision.draft.fields.date.applyTo(null) == null) add(VoiceMissingField.Date)
                        if (range.startAt == null) add(VoiceMissingField.Start)
                        if (range.endAt == null) add(VoiceMissingField.End)
                    }
                    else -> emptySet()
                }
                state.value = VoiceUiState(
                text = state.value.text,
                phase = VoiceInputPhase.ResolvingAmbiguity,
                draft = decision.draft,
                clarification = decision.reason,
                missing = needed,
                )
            }
        }
    }

    private suspend fun resolve(draft: VoiceActionDraft) {
        val required = draft.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord,
            VoiceAction.StopRecord, VoiceAction.FinishSleep, VoiceAction.CreateRecordFromEvent)
        val targets = if (required) VoiceTargetResolver(repository, zone).targets(draft) else emptyList()
        state.value = VoiceUiState(text = state.value.text, phase = VoiceInputPhase.Executing, draft = draft,
            targets = targets, selected = targets.singleOrNull(),
            chooseTarget = targets.size > 1,
            clarification = if (targets.size > 1) VoiceClarification.TargetNotUnique else null)
        if (required && targets.isEmpty()) {
            VoiceAgentDiagnosticsStore.validationResult("target_not_found")
            if (draft.action == VoiceAction.FinishSleep) {
                VoiceAgentDiagnosticsStore.reason(VoiceClarification.NoActiveSleep.name)
                state.update { it.copy(phase = VoiceInputPhase.ResolvingAmbiguity,
                    clarification = VoiceClarification.NoActiveSleep) }
                finishAgent(false, draft, "no_active_sleep")
                return
            }
            if (draft.action == VoiceAction.CreateRecordFromEvent) {
                state.update { it.copy(phase = VoiceInputPhase.ResolvingAmbiguity,
                    clarification = VoiceClarification.SourceEventMissing,
                    error = "没有找到对应日程。若继续创建普通记录，将不会关联来源日程。") }
                return
            }
            VoiceAgentDiagnosticsStore.reason(VoiceClarification.TargetNotFound.name)
            finishAgent(false, draft, "target_not_found")
            state.update { it.copy(clarification = VoiceClarification.TargetNotFound) }
            fail("没有找到要修改的内容"); return
        }
        if (targets.size > 1) {
            VoiceAgentDiagnosticsStore.validationResult("multiple_targets")
            finishAgent(false, draft, "multiple_targets")
        }
        attempt()
    }
    fun selectTarget(candidate: VoiceTargetCandidate?) = work {
        if (candidate != null && candidate !in state.value.targets) return@work
        state.update { it.copy(selected = candidate, chooseTarget = false) }
        attempt()
    }
    fun setDate(field: VoiceMissingField, date: LocalDate) = work {
        val d = state.value.draft ?: return@work
        val f = d.fields
        val updated = when (field) {
            VoiceMissingField.EndDate -> f.copy(endDate = VoiceFieldPatch.Set(date))
            else -> f.copy(date = VoiceFieldPatch.Set(date), startAt = null)
        }
        state.update { it.copy(draft = d.copy(fields = updated)) }
        routeByPolicy(d.copy(fields = updated, clarificationReason = null))
    }
    fun setTime(field: VoiceMissingField, time: LocalTime) = work {
        val d = state.value.draft ?: return@work
        val f = d.fields
        val updated = when (field) {
            VoiceMissingField.End -> d.copy(fields = f.copy(end = VoiceFieldPatch.Set(time), endAt = null), ambiguousEnd = false)
            else -> d.copy(fields = f.copy(start = VoiceFieldPatch.Set(time), startAt = null,
                date = if (d.action == VoiceAction.CreateRecord && f.date == VoiceFieldPatch.Keep)
                    VoiceFieldPatch.Set(Instant.ofEpochMilli(d.nowMillis).atZone(zone).toLocalDate()) else f.date),
                ambiguousStart = false)
        }
        state.update { it.copy(draft = updated) }
        routeByPolicy(updated.copy(clarificationReason = null))
    }
    private suspend fun attempt() {
        val s = state.value
        val d = s.draft ?: return
        if (s.chooseTarget) { state.update { it.copy(phase = VoiceInputPhase.ResolvingAmbiguity) }; return }
        when (val result = ExecuteVoiceActionUseCase(repository, zone,
            trace = VoiceAgentDiagnosticsStore::decision)(d, s.selected?.id, executionAttempt)) {
            is VoiceExecutionResult.Executed -> {
                VoiceAgentDiagnosticsStore.validationResult("success")
                finishAgent(true, d)
                submissionId = null
                val receipt = result.receipt
                state.value = VoiceUiState()
                receipts.send(receipt)
            }
            is VoiceExecutionResult.Fallback -> {
                VoiceAgentDiagnosticsStore.validationResult(result.prepared.error ?: result.prepared.missing.joinToString())
                finishAgent(false, d, "validation_failed")
                val sleepFallback = d.action == VoiceAction.CreateSleepRecord && result.prepared.missing.isNotEmpty()
                val sleepReason = if (sleepFallback) {
                    if (VoiceMissingField.Start in result.prepared.missing) VoiceClarification.SleepMissingStart
                    else VoiceClarification.SleepMissingEnd
                } else null
                sleepReason?.let { VoiceAgentDiagnosticsStore.reason(it.name) }
                val eventRecordFallback = d.action == VoiceAction.CreateRecordFromEvent &&
                    result.prepared.missing.isNotEmpty()
                val sourceEvent = if (eventRecordFallback) s.selected?.id?.let { repository.getEvent(it) } else null
                state.update { it.copy(draft = d, missing = result.prepared.missing,
                error = result.prepared.error, futureRecord = result.prepared.futureRecord,
                clarification = result.prepared.clarification ?: sleepReason ?: it.clarification,
                editor = if (sleepFallback) VoiceEditorRequest(
                    VoiceType.Record, null, VoiceEditorSeed(d), sleep = true,
                ) else if (sourceEvent != null) VoiceEditorRequest(
                    VoiceType.Record, null, VoiceEditorSeed(d, event = sourceEvent),
                ) else it.editor,
                phase = if (result.prepared.clarification != null || result.prepared.error == null)
                    VoiceInputPhase.ResolvingAmbiguity else VoiceInputPhase.Error) }
            }
        }
    }
    private suspend fun finishAgent(success: Boolean, draft: VoiceActionDraft, reason: String? = null) {
        val call = pendingAgentCall ?: return
        pendingAgentCall = null
        val result = when (draft.action) {
            VoiceAction.CreateEvent, VoiceAction.CreateRecord,
            VoiceAction.CreateRecordFromEvent, VoiceAction.CreateSleepRecord -> "created"
            VoiceAction.StartRecord, VoiceAction.StartSleep -> "started"
            VoiceAction.StopRecord, VoiceAction.FinishSleep -> "stopped"
            else -> "updated"
        }
        runCatching { call.complete(VoiceAgentToolOutput(success,
            result = result.takeIf { success },
            title = (draft.fields.title.applyTo(null) ?: draft.targetText).takeIf { success },
            reason = reason.takeUnless { success })) }
    }
    fun chooseClarificationOption(index: Int) = work {
        val option = state.value.draft?.clarificationOptions?.getOrNull(index) ?: return@work
        routeByPolicy(option.draft)
    }
    fun chooseType(type: VoiceType) = work {
        val d = state.value.draft ?: return@work
        val range = VoiceIntentPolicy.temporalRange(d, zone)
        val start = range.startAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val end = range.endAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        val f = d.fields
        val converted = when (type) {
            VoiceType.Event -> d.copy(action = VoiceAction.CreateEvent,
                fields = f.copy(date = start?.toLocalDate()?.let { VoiceFieldPatch.Set(it) } ?: f.date,
                    start = start?.toLocalTime()?.let { VoiceFieldPatch.Set(it) } ?: f.start,
                    endDate = end?.toLocalDate()?.let { VoiceFieldPatch.Set(it) } ?: VoiceFieldPatch.Keep,
                    end = end?.toLocalTime()?.let { VoiceFieldPatch.Set(it) } ?: VoiceFieldPatch.Keep,
                    startAt = null, endAt = null),
                semanticIntent = VoiceSemanticIntent.PlannedArrangement,
                eventTemporalType = if (end != null) VoiceEventTemporalType.INTERVAL
                    else if (start != null) VoiceEventTemporalType.POINT else VoiceEventTemporalType.DATE,
                recordTemporalType = null, clarificationReason = null)
            VoiceType.Record -> d.copy(action = VoiceAction.CreateRecord,
                fields = f.copy(startAt = range.startAt, endAt = range.endAt),
                semanticIntent = if (end == null) VoiceSemanticIntent.ReportedPoint else VoiceSemanticIntent.ReportedActivity,
                recordTemporalType = if (end == null) io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.POINT
                    else io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.INTERVAL,
                eventTemporalType = null, clarificationReason = null)
        }
        val warning = when {
            type == VoiceType.Record && start?.toInstant()?.toEpochMilli()?.let { it > d.nowMillis } == true ->
                "实际记录不能发生在未来；请确认真实发生时间。"
            else -> null
        }
        if (warning != null) state.update { it.copy(pendingTypeChange = converted, typeChangeWarning = warning,
            phase = VoiceInputPhase.ResolvingAmbiguity) }
        else routeByPolicy(converted)
    }
    fun confirmTypeChange() = work {
        val converted = state.value.pendingTypeChange ?: return@work
        state.update { it.copy(pendingTypeChange = null, typeChangeWarning = null) }
        routeByPolicy(converted)
    }
    fun cancelTypeChange() = work {
        state.update { it.copy(pendingTypeChange = null, typeChangeWarning = null) }
    }
    fun createRecordWithoutEvent() = work {
        val d = state.value.draft?.takeIf { it.action == VoiceAction.CreateRecordFromEvent } ?: return@work
        routeByPolicy(d.copy(action = VoiceAction.CreateRecord,
            fields = d.fields.copy(title = VoiceFieldPatch.Set(d.targetText)),
            clarificationReason = null))
    }
    fun createInstead() = work {
        val d = state.value.draft ?: return@work
        val action = when (d.action) {
            VoiceAction.UpdateEvent -> VoiceAction.CreateEvent
            VoiceAction.UpdateRecord -> VoiceAction.CreateRecord
            else -> return@work
        }
        routeByPolicy(d.copy(action = action, fields = d.fields.copy(title = VoiceFieldPatch.Set(d.targetText)),
            clarificationReason = null))
    }
    fun openEditor() = work {
        val s = state.value
        val d = s.draft ?: return@work
        if (s.chooseTarget) return@work
        val type = when (d.action) {
            VoiceAction.CreateEvent, VoiceAction.UpdateEvent -> VoiceType.Event
            VoiceAction.CreateRecord, VoiceAction.UpdateRecord, VoiceAction.StartRecord,
            VoiceAction.CreateRecordFromEvent, VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> VoiceType.Record
            else -> return@work
        }
        val id = s.selected?.id.takeIf { d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord) }
        if (d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord) && id == null) return@work
        val event = if (d.action == VoiceAction.CreateRecordFromEvent) s.selected?.id?.let { repository.getEvent(it) } else null
        val sleep = d.action in setOf(VoiceAction.CreateSleepRecord, VoiceAction.StartSleep)
        val classification = if (!sleep && id == null && event == null)
            io.github.peanutpersimmon.rilo.domain.usecase.InitialClassificationUseCase(repository)(
                io.github.peanutpersimmon.rilo.domain.usecase.InitialClassificationInput(d.fields.title.applyTo(null) ?: d.targetText, applyKeywordRules = true)) else null
        val seedDraft = if (d.action in setOf(VoiceAction.StartRecord, VoiceAction.StartSleep))
            d.copy(fields = d.fields.copy(end = VoiceFieldPatch.Clear, endDate = VoiceFieldPatch.Clear)) else d
        state.update { it.copy(editor = VoiceEditorRequest(
            type, id, VoiceEditorSeed(seedDraft, classification, event), sleep = sleep,
        )) }
    }
    suspend fun undo(receipt: VoiceExecutionReceipt): String? = try {
        when (val result = UndoVoiceActionUseCase(repository)(receipt.undo)) {
            is DomainResult.Success -> null
            is DomainResult.Failure -> result.error.userMessage()
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { "撤销失败，请重试" }
    private fun fail(message: String) = state.update {
        it.copy(phase = VoiceInputPhase.Error, error = message)
    }
    private fun work(block: suspend () -> Unit) {
        if (audioSubmission != null || !working.compareAndSet(false, true)) return
        state.update { it.copy(phase = VoiceInputPhase.Executing, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                pendingAgentCall?.cancel(); pendingAgentCall = null
                fail("操作失败，请重试")
            }
            finally { state.update { if (it.busy) it.copy(phase = VoiceInputPhase.ResolvingAmbiguity) else it }; working.set(false) }
        }
    }
    override fun onCleared() {
        cancelAudio()
        pendingAgentCall?.cancel()
        pendingAgentCall = null
        super.onCleared()
    }
    companion object {
        fun factory(repository: RidoCoreRepository, agent: VoiceAgentEngine? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = VoiceViewModel(repository, agent) as T
        }
    }
}
