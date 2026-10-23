package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId

class FinishSleepCheckInUseCase(
    private val repository: RidoCoreRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = { RuntimeZoneId.current },
) {
    suspend operator fun invoke(): DomainResult<Session> =
        repository.finishSleepCheckIn(
            endAtMillis = nowMillis().toMinutePrecision(),
            zoneId = zoneId(),
        )
}
