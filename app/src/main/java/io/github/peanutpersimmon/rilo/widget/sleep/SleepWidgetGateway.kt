package io.github.peanutpersimmon.rilo.widget.sleep

import android.content.Context
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.FinishSleepCheckInUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StartSleepCheckInUseCase
import java.time.ZoneId

class SleepWidgetGateway internal constructor(
    repository: RidoCoreRepository,
    nowMillis: () -> Long = System::currentTimeMillis,
    zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    constructor(context: Context) : this(
        repository = RoomRidoRepository(RidoDatabase.getInstance(context.applicationContext)),
    )

    private val startSleepCheckIn = StartSleepCheckInUseCase(repository, nowMillis)
    private val finishSleepCheckIn = FinishSleepCheckInUseCase(repository, nowMillis, zoneId)

    suspend fun sleepCheckIn(): DomainResult<Long> = startSleepCheckIn()

    suspend fun wakeCheckIn(): DomainResult<Session> = finishSleepCheckIn()
}
