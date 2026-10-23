package io.github.peanutpersimmon.rilo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rilo.presentation.plan.MonthContent
import io.github.peanutpersimmon.rilo.presentation.today.*
import io.github.peanutpersimmon.rilo.ui.components.TimelineZoomState
import io.github.peanutpersimmon.rilo.ui.feature.plan.RecordDay
import io.github.peanutpersimmon.rilo.ui.feature.plan.RecordWeek
import io.github.peanutpersimmon.rilo.ui.feature.plan.PlanMonth
import io.github.peanutpersimmon.rilo.ui.feature.plan.TodoDoneDay
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayTimeline
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompletedFactUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 9, 22)
    private val zone = ZoneId.systemDefault()
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val at = day.atTime(10, 36).atZone(zone).toInstant().toEpochMilli()

    @Test fun monthPointSummaryOpensRecordWithoutDuration() {
        var opened: Long? = null
        var dateOpened = false
        val point = Session(id = 87, title = "取快递", temporalType = SessionTemporalType.POINT,
            startAtMillis = at, createdAtMillis = at, updatedAtMillis = at)
        compose.setContent {
            RidoTheme {
                PlanMonth(PlanUiState(selectedDate = day, monthContent = MonthContent.RECORD,
                    records = listOf(point), loading = false), {}, { dateOpened = true },
                    { opened = it }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("取快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(87L, opened)
            assertEquals(false, dateOpened)
        }
    }

    @Test fun todayPointRecordShowsTitleAndOpensSessionWithoutDuration() {
        var opened: Long? = null
        val entry = TimelineEntryUiModel(42, at, null, "拿快递", "10:36", null,
            TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL,
            null, null, null, false)
        compose.setContent {
            RidoTheme {
                TodayTimeline(AwakeDayUiModel(start, start + 86_400_000, "00:00", "24:00",
                    wakeAtMillis = start, sleepAtMillis = start + 86_400_000),
                    listOf(entry), TimelineDisplayMode.OVERVIEW, at,
                    { opened = it.sourceId }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("拿快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle { assertEquals(42L, opened) }
    }

    @Test fun todoDonePointRecordShowsTitleAndOpensExistingRecordWithoutDuration() {
        val point = Session(id = 77, title = "取快递", kind = SessionKind.ACTIVITY,
            temporalType = SessionTemporalType.POINT, startAtMillis = at,
            endAtMillis = null, createdAtMillis = at, updatedAtMillis = at)
        var opened: Long? = null
        compose.setContent {
            RidoTheme {
                val zoom = remember { TimelineZoomState() }
                TodoDoneDay(PlanUiState(selectedDate = day, records = listOf(point),
                    loading = false, nowMillis = at,
                    awakeDisplayWindow = AwakeDisplayWindow(day,
                        io.github.peanutpersimmon.rilo.domain.time.TimeInterval(
                            start, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
                        null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)),
                    {}, { id, _ -> opened = id }, zoom, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("取快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle { assertEquals(77L, opened) }
    }

}
