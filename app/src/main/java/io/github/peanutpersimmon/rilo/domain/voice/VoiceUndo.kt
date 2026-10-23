package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository

/** Memory-only, short-lived Snackbar undo; no command history is persisted. */
data class VoiceDataSnapshot(val events: List<Event> = emptyList(), val records: List<Session> = emptyList())
data class VoiceUndo(val before: VoiceDataSnapshot, val after: VoiceDataSnapshot)
data class VoiceExecutionReceipt(val message: String, val undo: VoiceUndo)
internal class VoiceWriteRejected(val error: DomainError) : RuntimeException()
internal fun <T> DomainResult<T>.requireVoiceSuccess(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> throw VoiceWriteRejected(error)
}

class UndoVoiceActionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(undo: VoiceUndo): DomainResult<Unit> = try {
        repository.voiceTransaction {
            fun stale(): Nothing = throw VoiceWriteRejected(DomainError.DataIntegrity("内容已有后续变化，无法撤销"))
            undo.after.events.forEach { if (repository.getEvent(it.id) != it) stale() }
            undo.after.records.forEach { if (repository.getSession(it.id) != it) stale() }
            val newEvents = undo.after.events.filter { after -> undo.before.events.none { it.id == after.id } }
            if (newEvents.isNotEmpty()) {
                val sessions = repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE)
                if (newEvents.any { created -> sessions.any { it.sourceEventId == created.id } }) stale()
            }
            newEvents.forEach { repository.deleteEvent(it.id).requireVoiceSuccess() }
            undo.after.records.filter { after -> undo.before.records.none { it.id == after.id } }
                .forEach { repository.deleteSession(it.id).requireVoiceSuccess() }
            undo.before.events.forEach { repository.upsertEvent(it).requireVoiceSuccess() }
            undo.before.records.forEach { repository.updateSession(it).requireVoiceSuccess() }
            DomainResult.Success(Unit)
        }
    } catch (e: VoiceWriteRejected) { DomainResult.Failure(e.error) }
}
