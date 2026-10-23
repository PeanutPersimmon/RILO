package io.github.peanutpersimmon.rilo.presentation.editor.session

import androidx.lifecycle.ViewModel
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceAgentDiagnosticsStore
import io.github.peanutpersimmon.rilo.presentation.voice.apply
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.domain.voice.VoiceAction
import io.github.peanutpersimmon.rilo.domain.voice.applyTo
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rilo.domain.usecase.CreatePastSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.DeleteSessionWithUndoUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetEditorOptionsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetSessionForEditorUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.NewSessionInput
import io.github.peanutpersimmon.rilo.domain.usecase.RestoreSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.SaveSessionEditUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StartSessionUseCase
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import io.github.peanutpersimmon.rilo.domain.time.resolveLocalMinute
import java.time.DateTimeException
import io.github.peanutpersimmon.rilo.presentation.editor.EditorMode
import io.github.peanutpersimmon.rilo.presentation.editor.editorOptions
import io.github.peanutpersimmon.rilo.presentation.editor.toLocalDate
import io.github.peanutpersimmon.rilo.presentation.editor.toLocalTime
import io.github.peanutpersimmon.rilo.presentation.editor.userMessage
import io.github.peanutpersimmon.rilo.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionEditorViewModel(
    private val getSession: GetSessionForEditorUseCase,
    private val getOptions: GetEditorOptionsUseCase,
    private val createPast: CreatePastSessionUseCase,
    private val startSession: StartSessionUseCase,
    private val saveEdit: SaveSessionEditUseCase,
    private val deleteSession: DeleteSessionWithUndoUseCase,
    private val restoreSession: RestoreSessionUseCase,
    zoneId: ZoneId? = null,
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val getEvent: io.github.peanutpersimmon.rilo.domain.usecase.GetEventForEditorUseCase? = null,
    private val observeChanges: io.github.peanutpersimmon.rilo.domain.usecase.ObserveTimelineDataChangesUseCase? = null,
    private val getPreviousActualEnd: io.github.peanutpersimmon.rilo.domain.usecase.GetPreviousActualEndUseCase? = null,
    private val initialClassification: io.github.peanutpersimmon.rilo.domain.usecase.InitialClassificationUseCase? = null,
) : ViewModel() {
    private val fixedZoneId = zoneId
    private val zoneId: ZoneId get() = fixedZoneId ?: RuntimeZoneId.current
    private var observedZoneId = zoneId ?: RuntimeZoneId.current
    private val mutableState = MutableStateFlow(SessionEditorUiState())
    val uiState = mutableState.asStateFlow()
    private var original: Session? = null
    private var classificationJob: kotlinx.coroutines.Job? = null
    private var categoryManuallyChanged = false
    private var userSleepKind: SessionKind? = null
    private var voiceStart = false
    private var voiceSeed: VoiceEditorSeed? = null

    private var sourceWatch: kotlinx.coroutines.Job? = null

    init {
        if (fixedZoneId == null) {
            viewModelScope.launch {
                RuntimeZoneId.changes.collect { currentZone ->
                    val previousZone = observedZoneId
                    observedZoneId = currentZone
                    if (previousZone != currentZone) {
                        mutableState.update { it.reprojectTimeZone(previousZone, currentZone) }
                    }
                }
            }
        }
    }

    fun load(id: Long?, sourceEventId: Long? = null) = viewModelScope.launch {
        sourceWatch?.cancel()
        voiceStart = false
        voiceSeed = null
        val record = id?.let { getSession(it) }
        if (id != null && record == null) {
            mutableState.value = SessionEditorUiState(errorMessage = "内容已不存在，请关闭后重试")
            return@launch
        }
        original = record
        categoryManuallyChanged = false
        userSleepKind = null
        val options = getOptions()
        val now = clock.nowMillis()
        mutableState.value = record?.let {
            val session = it
            SessionEditorUiState(
                loading = false, mode = EditorMode.EDIT, id = session.id, title = session.title,
                startDate = session.startAtMillis.toLocalDate(zoneId), startTime = session.startAtMillis.toLocalTime(zoneId),
                endDate = session.endAtMillis?.toLocalDate(zoneId), endTime = session.endAtMillis?.toLocalTime(zoneId),
                startReferenceMillis = session.startAtMillis, endReferenceMillis = session.endAtMillis,
                temporalType = session.temporalType,
                wasActiveAtLoad = session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null,
                categoryId = session.categoryId.takeIf { session.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY },
                sourceEventId = session.sourceEventId.takeIf { session.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY },
                sourceEventTitle = session.sourceEventId?.takeIf { session.kind == io.github.peanutpersimmon.rilo.domain.model.SessionKind.ACTIVITY }
                    ?.let { getEvent?.invoke(it)?.title },
                kind = session.kind, location = session.location.orEmpty(), note = session.note.orEmpty(),
                options = editorOptions(options.categories),
            )
        } ?: run {
            val editorNow = now.toMinutePrecision()
            val dayStart = editorNow.toLocalDate(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val previousEnd = if (sourceEventId == null) getPreviousActualEnd?.invoke(dayStart, editorNow) else null
            SessionEditorUiState(
                loading = false,
                startDate = previousEnd?.toLocalDate(zoneId) ?: editorNow.toLocalDate(zoneId),
                startTime = previousEnd?.toLocalTime(zoneId) ?: editorNow.toLocalTime(zoneId),
                endDate = previousEnd?.let { editorNow.toLocalDate(zoneId) },
                endTime = previousEnd?.let { editorNow.toLocalTime(zoneId) },
                startReferenceMillis = previousEnd ?: editorNow,
                endReferenceMillis = previousEnd?.let { editorNow },
                temporalType = if (previousEnd != null) SessionTemporalType.INTERVAL else SessionTemporalType.POINT,
                options = editorOptions(options.categories),
            )
        }
        if (id == null && sourceEventId != null) prefillEvent(sourceEventId)
        sourceWatch = viewModelScope.launch {
            observeChanges?.invoke()?.collect {
                val source = if (id != null) getSession(id)?.sourceEventId else mutableState.value.sourceEventId
                val event = source?.let { getEvent?.invoke(it) }
                update { it.copy(sourceEventId = event?.id, sourceEventTitle = event?.title) }
            }
        }
    }

    private suspend fun prefillEvent(eventId: Long) {
        val e = getEvent?.invoke(eventId) ?: return
        val now = clock.nowMillis()
        var timeError: String? = null
        fun plannedMinute(day: Long, minute: Int): Long? = try {
            resolveLocalMinute(java.time.LocalDate.ofEpochDay(day), java.time.LocalTime.MIN.plusMinutes(minute.toLong()), zoneId)
        } catch (error: DateTimeException) {
            timeError = error.message
            null
        }
        val start = e.startMinuteOfDay?.let { plannedMinute(e.dateEpochDay, it) }
        val end = e.endMinuteOfDay?.let {
            plannedMinute(e.endDateEpochDay ?: e.dateEpochDay, it)
        }
        update { state ->
            val legalStart = start?.takeIf { it <= now }
            val legalEnd = end?.takeIf { legalStart != null && it > legalStart && it <= now }
            state.copy(title = e.title, note = e.note.orEmpty(), location = e.location.orEmpty(),
                categoryId = e.categoryId, sourceEventId = e.id, sourceEventTitle = e.title,
                startDate = legalStart?.toLocalDate(zoneId) ?: java.time.LocalDate.ofEpochDay(e.dateEpochDay),
                startTime = legalStart?.toLocalTime(zoneId) ?: state.startTime,
                startTimeConfirmed = legalStart != null,
                endDate = legalEnd?.toLocalDate(zoneId),
                endTime = legalEnd?.toLocalTime(zoneId),
                startReferenceMillis = legalStart, endReferenceMillis = legalEnd,
                temporalType = if (e.endMinuteOfDay == null) SessionTemporalType.POINT else SessionTemporalType.INTERVAL,
                requiresCompletedIntervalEnd = e.endMinuteOfDay != null && legalEnd == null,
                requiredIntervalEndLimitMillis = now,
                requiredIntervalZoneId = zoneId,
                errorMessage = timeError)
        }
    }

    fun loadSleep(id: Long?) = viewModelScope.launch {
        load(id).join()
        if (id == null) {
            val end = clock.nowMillis().toMinutePrecision()
            val start = getPreviousActualEnd?.invoke(Long.MIN_VALUE, end) ?: end
            mutableState.update { it.copy(
                title = "夜间睡眠", kind = io.github.peanutpersimmon.rilo.domain.model.SessionKind.NIGHT_SLEEP,
                startDate = start.toLocalDate(zoneId), startTime = start.toLocalTime(zoneId),
                endDate = end.toLocalDate(zoneId), endTime = end.toLocalTime(zoneId),
                startReferenceMillis = start, endReferenceMillis = end,
                temporalType = SessionTemporalType.INTERVAL,
                categoryId = null,
            ) }
        }
    }

    fun applyVoice(seed: VoiceEditorSeed) {
        voiceStart = true
        voiceSeed = seed
        mutableState.update { seed.apply(it, zoneId).copy(
            startReferenceMillis = seed.draft.fields.startAt,
            endReferenceMillis = seed.draft.fields.endAt,
        ) }
    }

    fun applySleepVoice(seed: VoiceEditorSeed) {
        voiceStart = seed.draft.action == VoiceAction.StartSleep
        voiceSeed = seed
        userSleepKind = null
        mutableState.update { current ->
            val applied = seed.apply(current, zoneId)
            val fields = seed.draft.fields
            val range = io.github.peanutpersimmon.rilo.domain.voice.VoiceIntentPolicy.temporalRange(seed.draft, zoneId)
            val kind = SleepKindResolver.resolve(fields.sleepKind, range.startAt, range.endAt,
                zoneId, seed.draft.originalText) ?: SleepKindResolver.activeStorageKind(null)
            val hasExplicitEnd = fields.endAt != null || fields.end !is io.github.peanutpersimmon.rilo.domain.voice.VoiceFieldPatch.Keep
            applied.copy(
                startReferenceMillis = fields.startAt,
                endReferenceMillis = fields.endAt,
                title = fields.title.applyTo(null) ?: if (kind == SessionKind.NAP) "小憩" else "夜间睡眠",
                kind = kind,
                endDate = applied.endDate.takeIf {
                    seed.draft.action != VoiceAction.StartSleep && hasExplicitEnd
                },
                endTime = applied.endTime.takeIf {
                    seed.draft.action != VoiceAction.StartSleep && hasExplicitEnd
                },
                categoryId = null,
                sourceEventId = null,
                sourceEventTitle = null,
                temporalType = SessionTemporalType.INTERVAL,
            )
        }
    }

    fun update(transform: (SessionEditorUiState) -> SessionEditorUiState) = mutableState.update(transform)
    fun changeTitle(value: String) {
        update { it.copy(title = value) }
        classificationJob?.cancel()
        if (original?.categoryId != null || mutableState.value.kind != SessionKind.ACTIVITY || initialClassification == null) return
        classificationJob = viewModelScope.launch {
            kotlinx.coroutines.delay(180)
            val match = initialClassification(io.github.peanutpersimmon.rilo.domain.usecase.InitialClassificationInput(value, applyKeywordRules = true))
            mutableState.update { state -> if (state.title != value || state.kind != SessionKind.ACTIVITY) state else state.copy(
                categoryId = if (categoryManuallyChanged) state.categoryId else match.categoryId,
            ) }
        }
    }
    fun chooseCategory(value: Long?, kind: SessionKind) {
        categoryManuallyChanged = true
        update { it.copy(categoryId = value, kind = kind) }
    }
    fun chooseSleepKind(kind: SessionKind) {
        userSleepKind = kind
        update { it.copy(kind = kind, title = io.github.peanutpersimmon.rilo.domain.service.SleepCheckInPolicy.titleFor(kind),
            categoryId = null) }
    }
    fun setSleepStart(date: java.time.LocalDate, time: java.time.LocalTime) =
        mutableState.update { inferSleepKind(it.copy(startDate = date, startTime = time)) }
    fun setSleepEnd(date: java.time.LocalDate, time: java.time.LocalTime) =
        mutableState.update { inferSleepKind(it.copy(endDate = date, endTime = time)) }
    private fun inferSleepKind(state: SessionEditorUiState): SessionEditorUiState {
        if (state.kind == SessionKind.ACTIVITY) return state
        val endDate = state.endDate ?: return state
        val endTime = state.endTime ?: return state
        val start = try { resolveLocalMinute(state.startDate, state.startTime, zoneId, state.startReferenceMillis) }
            catch (error: DateTimeException) { return state.copy(errorMessage = error.message) }
        val end = try { resolveLocalMinute(endDate, endTime, zoneId, state.endReferenceMillis) }
            catch (error: DateTimeException) { return state.copy(errorMessage = error.message) }
        if (end <= start) return state
        val kind = SleepKindResolver.resolve(voiceSeed?.draft?.fields?.sleepKind, start, end,
            zoneId, voiceSeed?.draft?.originalText.orEmpty(), userSleepKind) ?: return state
        return state.copy(kind = kind,
            title = io.github.peanutpersimmon.rilo.domain.service.SleepCheckInPolicy.titleFor(kind))
    }
    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    fun clearEnd() = mutableState.update(SessionEditorUiState::withoutEnd)
    fun setEnd(date: java.time.LocalDate?, time: java.time.LocalTime?) =
        mutableState.update { it.withEnd(date, time).copy(
            requiredIntervalEndLimitMillis = if (it.requiresCompletedIntervalEnd) clock.nowMillis()
                else it.requiredIntervalEndLimitMillis,
        ) }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        val start: Long
        val end: Long?
        try {
            start = resolveLocalMinute(state.startDate, state.startTime, zoneId, state.startReferenceMillis)
            end = if (state.endDate != null && state.endTime != null)
                resolveLocalMinute(state.endDate, state.endTime, zoneId, state.endReferenceMillis) else null
        } catch (error: DateTimeException) {
            mutableState.update { it.copy(saving = false, errorMessage = error.message) }
            return@launch
        }
        if (state.mode == EditorMode.CREATE && !state.canSavePast || state.mode == EditorMode.EDIT && !state.canSaveEdit) return@launch
        val now = clock.nowMillis()
        if (start > now || end?.let { it > now } == true) {
            mutableState.update { it.copy(saving = false, errorMessage = "记录时间不能晚于当前时间") }
            return@launch
        }
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        val result = if (state.mode == EditorMode.CREATE) {
            val historical = input(state, start, end).copy(temporalType = state.saveTemporalType)
            createPast(historical)
        } else {
            val base = requireNotNull(original)
            saveEdit(
                Session(
                    id = requireNotNull(state.id), title = state.title, note = state.note.ifBlank { null },
                    location = state.location.ifBlank { null }, kind = state.kind,
                    temporalType = state.saveTemporalType, startAtMillis = start,
                    endAtMillis = end, categoryId = state.categoryId,
                    sourceEventId = state.sourceEventId,
                    createdAtMillis = base.createdAtMillis, updatedAtMillis = clock.nowMillis(),
                ),
            )
        }
        handle(result, onSuccess)
    }

    fun startNow(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        if (!state.canStartNow) return@launch
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        handle(startSession(input(state, clock.nowMillis().toMinutePrecision(), null).copy(
            temporalType = SessionTemporalType.INTERVAL,
        ), requireIdle = voiceStart), onSuccess)
    }

    fun delete(onDeleted: (DeletedSessionSnapshot) -> Unit) = viewModelScope.launch {
        when (val result = deleteSession(mutableState.value.id ?: return@launch)) {
            is DomainResult.Success -> onDeleted(result.value)
            is DomainResult.Failure -> mutableState.update { it.copy(errorMessage = result.error.userMessage()) }
        }
    }

    suspend fun restore(snapshot: DeletedSessionSnapshot): Boolean = restoreSession(snapshot) is DomainResult.Success

    private fun input(state: SessionEditorUiState, start: Long, end: Long?) = NewSessionInput(
        title = state.title, startAtMillis = start, endAtMillis = end,
        sourceEventId = state.sourceEventId,
        categoryId = state.categoryId, note = state.note.ifBlank { null },
        location = state.location.ifBlank { null }, kind = state.kind, createdAtMillis = clock.nowMillis(),
    )

    private fun <T> handle(result: DomainResult<T>, onSuccess: () -> Unit) {
        when (result) {
            is DomainResult.Success -> {
                if (voiceSeed != null) {
                    VoiceAgentDiagnosticsStore.decision("executionAction", "SessionEditorSave")
                    VoiceAgentDiagnosticsStore.decision("savedDomainType",
                        if (mutableState.value.kind == SessionKind.ACTIVITY) "RECORD" else "SLEEP")
                }
                onSuccess()
            }
            is DomainResult.Failure -> mutableState.update { it.copy(saving = false, errorMessage = result.error.userMessage()) }
        }
    }
}
