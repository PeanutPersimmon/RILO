package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.DomainError
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import io.github.peanutpersimmon.rilo.domain.time.withMinutePrecision
import kotlinx.coroutines.flow.Flow

data class NewSessionInput(
    val title: String,
    val startAtMillis: Long,
    val endAtMillis: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val location: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val createdAtMillis: Long = startAtMillis,
    val sourceEventId: Long? = null,
    val temporalType: SessionTemporalType = SessionTemporalType.INTERVAL,
)

private fun NewSessionInput.toSession(): Session = Session(
    title = title,
    note = note,
    location = location,
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis.toMinutePrecision(),
    endAtMillis = endAtMillis?.toMinutePrecision(),
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = createdAtMillis,
)

class StartSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: NewSessionInput, requireIdle: Boolean = false): DomainResult<Long> =
        if (input.temporalType != SessionTemporalType.INTERVAL) DomainResult.Failure(DomainError.InvalidTime)
        else if (requireIdle) repository.startSessionIfIdle(input.copy(endAtMillis = null).toSession())
        else repository.startSession(input.copy(endAtMillis = null).toSession())
}

class StopActiveSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(endAtMillis: Long): DomainResult<Session> =
        repository.stopActiveSession(endAtMillis.toMinutePrecision())
}

class CreatePastSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: NewSessionInput): DomainResult<Long> =
        repository.createPastSession(input.toSession())
}

/** Sleep shares Session persistence while enforcing its product boundary at the write edge. */
class CreateSleepSessionUseCase(private val repository: RidoCoreRepository) {
    suspend fun create(input: NewSessionInput): DomainResult<Long> {
        val sleep = input.asSleep() ?: return unavailable()
        return CreatePastSessionUseCase(repository)(sleep)
    }

    suspend fun start(input: NewSessionInput): DomainResult<Long> {
        val sleep = input.asSleep() ?: return unavailable()
        return StartSessionUseCase(repository)(sleep, requireIdle = true)
    }

    private fun NewSessionInput.asSleep(): NewSessionInput? =
        takeIf { it.kind in setOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP) &&
            it.temporalType == SessionTemporalType.INTERVAL }?.copy(
            temporalType = SessionTemporalType.INTERVAL,
            categoryId = null,
            sourceEventId = null,
        )

    private fun unavailable(): DomainResult<Long> =
        DomainResult.Failure(DomainError.DataIntegrity("当前无法创建睡眠记录"))
}

class UpdateSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(session: Session): DomainResult<Unit> =
        repository.updateSession(session.withMinutePrecision())
}

class DeleteSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(sessionId: Long): DomainResult<Unit> =
        repository.deleteSession(sessionId)
}

class ObserveActiveSessionUseCase(private val repository: RidoCoreRepository) {
    operator fun invoke(): Flow<Session?> = repository.observeActiveSession()
}
