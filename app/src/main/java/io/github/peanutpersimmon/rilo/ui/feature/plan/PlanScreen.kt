package io.github.peanutpersimmon.rilo.ui.feature.plan

import io.github.peanutpersimmon.rilo.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.presentation.plan.MonthContent
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.ui.components.CompactCapsule
import io.github.peanutpersimmon.rilo.ui.components.CompactDateNavigation
import io.github.peanutpersimmon.rilo.ui.components.ScrollablePrimaryNavigation
import io.github.peanutpersimmon.rilo.ui.components.PageHeader
import io.github.peanutpersimmon.rilo.ui.components.PageNavBand
import io.github.peanutpersimmon.rilo.ui.components.SegmentedControlLevel
import io.github.peanutpersimmon.rilo.ui.components.discreteHorizontalSwipe
import io.github.peanutpersimmon.rilo.ui.components.picker.AppCalendarDialog
import io.github.peanutpersimmon.rilo.ui.components.picker.CalendarSelectionMode
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.first

@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    contentPadding: PaddingValues,
    onEventClick: (Long) -> Unit,
    onRecordClick: (Long, SessionKind) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val planModes by viewModel.viewOrder.collectAsStateWithLifecycle()
    val d = AppTheme.dimens
    var datePickerOpen by remember { mutableStateOf(false) }
    if (!state.selectionReady || viewModel.isSelectionStale(state)) {
        Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        PlanHeader(
            state = state,
            planModes = planModes,
            onMode = viewModel::selectMode,
            onStep = viewModel::step,
            onDateClick = { datePickerOpen = true },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .discreteHorizontalSwipe { direction ->
                    viewModel.stepMode(direction.step)
                }
        ) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val zoomDate = state.selectedDate.takeIf {
                        state.mode == PlanViewMode.TODO_DONE
                    }
                    val pageZoom = remember(state.mode, zoomDate) { PlanTimelineZoomState() }
                    when (state.mode) {
                            PlanViewMode.DAY -> RecordDay(
                                state.selectedDate,
                                state.records,
                                state.categoryColorKeys,
                                onRecordClick,
                                pageZoom,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                                zoneId = state.zoneId,
                            )
                            PlanViewMode.WEEK -> RecordWeek(
                                state.selectedDate,
                                state.records,
                                state.categoryColorKeys,
                                onRecordClick,
                                pageZoom,
                                Modifier.fillMaxSize(),
                                zoneId = state.zoneId,
                            )
                            PlanViewMode.MONTH -> PlanMonth(
                                state,
                                viewModel::selectMonthContent,
                                onDate = { date ->
                                    viewModel.openMonthDate(date,
                                        if (state.monthContent == MonthContent.RECORD) {
                                            PlanViewMode.DAY
                                        } else {
                                            PlanViewMode.TODO_DONE
                                        }
                                    )
                                },
                                onPointRecordClick = { id -> onRecordClick(id, SessionKind.ACTIVITY) },
                                modifier = Modifier.fillMaxSize().padding(top = d.planMonthGap),
                            )
                            PlanViewMode.BOARD -> PlanBoard(
                                state,
                                onEventClick,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                            )
                            PlanViewMode.TODO_DONE -> TodoDoneDay(
                                state,
                                onEventClick,
                                onRecordClick,
                                pageZoom,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                            )
                    }
                }
            }
        }
    }
    if (datePickerOpen) {
        AppCalendarDialog(state.selectedDate, CalendarSelectionMode.DAY,
            onDismiss = { datePickerOpen = false },
            onConfirm = { viewModel.selectDate(it); datePickerOpen = false },
            confirmDayOnClick = true)
    }
}

@Composable
private fun PlanHeader(
    state: PlanUiState,
    planModes: List<PlanViewMode>,
    onMode: (PlanViewMode) -> Unit,
    onStep: (Long) -> Unit,
    onDateClick: () -> Unit,
) {
    val d = io.github.peanutpersimmon.rilo.ui.theme.AppTheme.dimens
    Column(Modifier.fillMaxWidth().padding(top = d.pageHeaderTop)) {
        PageHeader {
            ScrollablePrimaryNavigation(
                planModes,
                state.mode,
                { it.name },
                { it.label },
                onMode,
            )
        }
        Spacer(Modifier.height(d.spaceMicro))
        CompactDateNavigation(timeTitle(state), { onStep(-1) }, { onStep(1) }, onDateClick,
            Modifier.padding(horizontal = d.pageHorizontal),
            touchTarget = io.github.peanutpersimmon.rilo.ui.components.DATE_NAVIGATION_TOUCH_TARGET)
    }
}

private fun timeTitle(state: PlanUiState): String {
    val date = state.selectedDate
    return when (state.mode) {
        PlanViewMode.DAY, PlanViewMode.TODO_DONE ->
            date.format(DateTimeFormatter.ofPattern("MMM d · EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        PlanViewMode.WEEK, PlanViewMode.BOARD -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            "${monday.monthValue}.${monday.dayOfMonth} – ${sunday.monthValue}.${sunday.dayOfMonth} · 第${monday.get(WeekFields.ISO.weekOfWeekBasedYear())}周"
        }
        PlanViewMode.MONTH -> "${date.year}年${date.monthValue}月"
    }
}
