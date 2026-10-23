package io.github.peanutpersimmon.rilo.presentation.editor.session

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionEditorTimeZoneTest {
    @Test fun laterOverlapOccurrenceSurvivesRuntimeZoneRoundTrip() {
        val la = ZoneId.of("America/Los_Angeles")
        val shanghai = ZoneId.of("Asia/Shanghai")
        val instant = java.time.Instant.parse("2026-11-01T09:30:00Z").toEpochMilli()
        val original = SessionEditorUiState(loading = false,
            startDate = LocalDate.of(2026, 11, 1), startTime = LocalTime.of(1, 30),
            startReferenceMillis = instant)
        val roundTrip = original.reprojectTimeZone(la, shanghai).reprojectTimeZone(shanghai, la)
        assertEquals(original.startDate, roundTrip.startDate)
        assertEquals(original.startTime, roundTrip.startTime)
        assertEquals(instant, roundTrip.startReferenceMillis)
    }
    @Test
    fun openEditorTimesKeepTheirInstantsWhenRuntimeZoneChanges() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val honolulu = ZoneId.of("Pacific/Honolulu")
        val state = SessionEditorUiState(
            loading = false,
            startDate = LocalDate.of(2026, 10, 18),
            startTime = LocalTime.of(15, 37),
            endDate = LocalDate.of(2026, 10, 18),
            endTime = LocalTime.of(15, 47),
            requiredIntervalZoneId = shanghai,
        )

        val changed = state.reprojectTimeZone(shanghai, honolulu)

        assertEquals(LocalDate.of(2026, 10, 17), changed.startDate)
        assertEquals(LocalTime.of(21, 37), changed.startTime)
        assertEquals(LocalDate.of(2026, 10, 17), changed.endDate)
        assertEquals(LocalTime.of(21, 47), changed.endTime)
        assertEquals(honolulu, changed.requiredIntervalZoneId)
        assertEquals(
            state.startDate.atTime(state.startTime).atZone(shanghai).toInstant(),
            changed.startDate.atTime(changed.startTime).atZone(honolulu).toInstant(),
        )
    }

    @Test
    fun activeIntervalKeepsMissingEndAcrossRuntimeZoneChange() {
        val changed = SessionEditorUiState(
            loading = false,
            startDate = LocalDate.of(2026, 10, 18),
            startTime = LocalTime.of(15, 37),
        ).reprojectTimeZone(
            ZoneId.of("Asia/Shanghai"),
            ZoneId.of("Pacific/Honolulu"),
        )

        assertEquals(LocalDate.of(2026, 10, 17), changed.startDate)
        assertEquals(LocalTime.of(21, 37), changed.startTime)
        assertEquals(null, changed.endDate)
        assertEquals(null, changed.endTime)
    }
}
