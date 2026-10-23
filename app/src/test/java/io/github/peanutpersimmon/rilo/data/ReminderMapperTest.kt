package io.github.peanutpersimmon.rilo.data

import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.data.mapper.toEntity
import io.github.peanutpersimmon.rilo.domain.model.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMapperTest {
    @Test fun eventOffsetSurvivesRoomMapperRoundTrip() {
        val event = Event(title = "日程", dateEpochDay = 1, startMinuteOfDay = 840,
            startReminderOffsetMinutes = 60, createdAtMillis = 1, updatedAtMillis = 1)
        assertEquals(event, event.toEntity().toDomain())
    }
}
