package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SleepKindResolverTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 15)
    private fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun oneResolverAppliesUserExplicitIntervalModelThenUnresolvedPriority() {
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(null, at(13), at(14), zone))
        assertEquals(SessionKind.NIGHT_SLEEP, SleepKindResolver.resolve(null, at(1), at(9), zone))
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(null, null, null, zone, "午睡了一小时"))
        assertEquals(SessionKind.NIGHT_SLEEP, SleepKindResolver.resolve(null, null, null, zone, "昨晚一点睡"))
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(SessionKind.NAP, null, null, zone))
        assertNull(SleepKindResolver.resolve(null, null, null, zone, "睡了一会"))
        assertEquals(SessionKind.NIGHT_SLEEP, SleepKindResolver.activeStorageKind(null))
        assertEquals(SessionKind.NAP, SleepKindResolver.resolve(SessionKind.NIGHT_SLEEP, at(1), at(9),
            zone, userKind = SessionKind.NAP))
        assertEquals(SessionKind.NIGHT_SLEEP, SleepKindResolver.resolve(null, at(13), at(18),
            zone, userKind = SessionKind.NIGHT_SLEEP))
    }

}
