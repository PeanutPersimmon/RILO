package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

data class VoiceTargetCandidate(val id: Long, val type: VoiceType, val title: String, val label: String)

/** No ranking, confidence, or history-based inference: all substring matches remain visible. */
object VoiceTargetMatching {
    fun matches(query: String, title: String): Boolean = query.trim().isNotEmpty() &&
        title.contains(query.trim(), ignoreCase = true)
    fun unique(candidates: List<VoiceTargetCandidate>): VoiceTargetCandidate? = candidates.singleOrNull()
}

class VoiceTargetResolver(private val repository: RidoCoreRepository, private val zone: ZoneId = ZoneId.systemDefault()) {
    suspend fun targets(draft: VoiceActionDraft): List<VoiceTargetCandidate> {
        val query = draft.targetText
        return when (draft.action) {
            VoiceAction.UpdateEvent, VoiceAction.CreateRecordFromEvent -> {
                val copy = draft.action == VoiceAction.CreateRecordFromEvent
                val first = if (copy && draft.targetDate != null) draft.targetDate.toEpochDay()
                    else LocalDate.of(1900, 1, 1).toEpochDay()
                val last = if (copy && draft.targetDate != null) draft.targetDate.toEpochDay()
                    else LocalDate.of(2200, 1, 1).toEpochDay()
                repository.getEvents(first, last, Long.MIN_VALUE, Long.MAX_VALUE)
                    .filter { VoiceTargetMatching.matches(query, it.title) }
                    .filter { draft.targetDate == null || it.dateEpochDay == draft.targetDate.toEpochDay() }
                    .filter { draft.targetStart == null || it.startMinuteOfDay == draft.targetStart.toSecondOfDay() / 60 }
                    .map { e ->
                        VoiceTargetCandidate(e.id, VoiceType.Event, e.title,
                            "${LocalDate.ofEpochDay(e.dateEpochDay)} ${e.startMinuteOfDay?.let { m -> "%02d:%02d".format(m / 60, m % 60) }.orEmpty()} · ${e.title} · #${e.id}") }
            }
            VoiceAction.UpdateRecord -> repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE)
                .filter { it.kind == SessionKind.ACTIVITY }
                .filter { VoiceTargetMatching.matches(query, it.title) }
                .filter { draft.targetStartAt == null || it.startAtMillis == draft.targetStartAt }
                .map { s -> VoiceTargetCandidate(s.id, VoiceType.Record, s.title,
                    "${Instant.ofEpochMilli(s.startAtMillis).atZone(zone).toLocalDateTime()} · ${s.title} · #${s.id}") }
            VoiceAction.StopRecord -> listOfNotNull(repository.observeActiveSession().first())
                .filter { it.kind == SessionKind.ACTIVITY }
                .filter { query.isBlank() || VoiceTargetMatching.matches(query, it.title) }
                .map { VoiceTargetCandidate(it.id, VoiceType.Record, it.title, "进行中 · ${it.title}") }
            VoiceAction.FinishSleep -> listOfNotNull(repository.observeActiveSession().first())
                .filter { it.kind in setOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP) &&
                    it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }
                .map { VoiceTargetCandidate(it.id, VoiceType.Record, it.title, "进行中的睡眠 · ${it.title}") }
            else -> emptyList()
        }
    }

}
