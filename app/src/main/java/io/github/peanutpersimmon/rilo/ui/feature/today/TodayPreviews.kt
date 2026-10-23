package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rilo.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rilo.domain.model.TimelineState
import io.github.peanutpersimmon.rilo.presentation.today.AwakeDayUiModel
import io.github.peanutpersimmon.rilo.presentation.today.TodayPlanItemUiModel
import io.github.peanutpersimmon.rilo.presentation.today.TodayPlanSummaryUiModel
import io.github.peanutpersimmon.rilo.presentation.today.TodayUiState
import io.github.peanutpersimmon.rilo.presentation.today.TimelineDisplayMode
import io.github.peanutpersimmon.rilo.presentation.today.TimelineEntryUiModel
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme

@Preview(name = "320dp phone", showBackground = true, widthDp = 320, heightDp = 640)
@Preview(name = "320dp large text", showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun TodayOverviewPreview() {
    RidoTheme {
        TodayScreenContent(
            uiState = previewState(),
            contentPadding = PaddingValues(),
            onEntryClick = {},
        )
    }
}

private fun previewState(): TodayUiState.Content {
    val hour = 3_600_000L
    val start = 1_758_326_400_000L
    return TodayUiState.Content(
        dateLabel = "20\nSEP · SUN",
        awakeDay = AwakeDayUiModel(start, start + 17 * hour, "07:00", "00:00"),
        timelineEntries = listOf(
            TimelineEntryUiModel(
                sourceId = null,
                startAtMillis = start,
                endAtMillis = null,
                title = "Wake",
                timeLabel = "07:00",
                durationLabel = null,
                entryType = TimelineEntryType.WAKE_POINT,
                state = TimelineState.BOUNDARY,
                categoryLabel = null,
                location = null,
                note = null,
                isActive = false,
            ),
            TimelineEntryUiModel(
                sourceId = 1,
                startAtMillis = start + 2 * hour,
                endAtMillis = start + 4 * hour,
                title = "做 PPT",
                timeLabel = "09:00–11:00",
                durationLabel = "2h",
                entryType = TimelineEntryType.SESSION_BLOCK,
                state = TimelineState.ACTUAL,
                categoryLabel = "工作",
                location = null,
                note = null,
                isActive = true,
            ),
        ),
        activeSessionId = 1,
        planSummary = TodayPlanSummaryUiModel(
            remainingCount = 1,
            nextTimeLabel = "14:00–15:00",
            nextTitle = "组会",
            items = listOf(TodayPlanItemUiModel(2, "14:00–15:00", "组会")),
        ),
        displayMode = TimelineDisplayMode.OVERVIEW,
        nowMillis = start + 4 * hour,
    )
}
