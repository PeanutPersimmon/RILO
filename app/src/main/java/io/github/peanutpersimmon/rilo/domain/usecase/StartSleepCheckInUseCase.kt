package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision

class StartSleepCheckInUseCase(
    repository: RidoCoreRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val createSleepSession = CreateSleepSessionUseCase(repository)

    suspend operator fun invoke(): DomainResult<Long> {
        val now = nowMillis().toMinutePrecision()
        return createSleepSession.start(
            NewSessionInput(
                title = NIGHT_SLEEP_TITLE,
                startAtMillis = now,
                endAtMillis = null,
                categoryId = null,
                note = null,
                location = null,
                kind = SessionKind.NIGHT_SLEEP,
                createdAtMillis = now,
                sourceEventId = null,
            ),
        )
    }

    private companion object {
        const val NIGHT_SLEEP_TITLE = "夜间睡眠"
    }
}
