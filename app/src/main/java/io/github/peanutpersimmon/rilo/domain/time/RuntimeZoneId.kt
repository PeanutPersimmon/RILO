package io.github.peanutpersimmon.rilo.domain.time

import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One refreshable source for the process-wide system timezone. */
object RuntimeZoneId {
    private val mutableZone = MutableStateFlow(ZoneId.systemDefault())

    val changes: StateFlow<ZoneId> = mutableZone.asStateFlow()
    val current: ZoneId get() = mutableZone.value

    fun refresh(zoneId: ZoneId = ZoneId.systemDefault()): ZoneId {
        mutableZone.value = zoneId
        return zoneId
    }
}
