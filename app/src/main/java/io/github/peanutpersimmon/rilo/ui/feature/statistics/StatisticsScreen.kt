package io.github.peanutpersimmon.rilo.ui.feature.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsSection
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsSelection
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsUiState
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rilo.ui.components.CompactPrimaryNavigation
import io.github.peanutpersimmon.rilo.ui.components.CompactSectionSurface
import io.github.peanutpersimmon.rilo.ui.components.PageHeader
import io.github.peanutpersimmon.rilo.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rilo.ui.components.discreteHorizontalSwipe
import io.github.peanutpersimmon.rilo.ui.components.picker.AppCalendarDialog
import io.github.peanutpersimmon.rilo.ui.components.picker.CalendarSelectionMode
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    contentPadding: PaddingValues,
    active: Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val d = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(top = d.pageHeaderTop)
    ) {
        when (val current = state) {
            is StatisticsUiState.Error ->
                Text(
                    current.message,
                    Modifier.padding(horizontal = d.pageHorizontal),
                    style = AppTheme.type.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            StatisticsUiState.Loading -> {
                Text("正在读取统计…",
                    modifier = Modifier.padding(horizontal = d.pageHorizontal),
                    style = AppTheme.type.secondary)
            }
            is StatisticsUiState.Ready -> if (viewModel.isPageStale(current)) {
                Text("正在读取统计…",
                    modifier = Modifier.padding(horizontal = d.pageHorizontal),
                    style = AppTheme.type.secondary)
            } else StatisticsReadyContent(current.selection, current, viewModel, active)
        }
    }
}

@Composable
private fun StatisticsReadyContent(
    selection: StatisticsSelection,
    ready: StatisticsUiState.Ready,
    viewModel: StatisticsViewModel,
    active: Boolean,
) {
    val sections = StatisticsSection.entries
    var dateDialog by remember { mutableStateOf(false) }
    val categoryScroll = rememberScrollState()
    val selectedSection = selection.section
    val d = AppTheme.dimens

    BackHandler(
        enabled = active && selection.detailId(selectedSection) != null,
    ) {
        viewModel.closeDetail(selectedSection)
    }
    LaunchedEffect(selection.categoryDetailId, selection.period) {
        categoryScroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
        // The level-one tabs live in the shared fixed-height header so this page's first
        // row matches Home and Settings.
        PageHeader {
            CompactPrimaryNavigation(
                sections,
                selectedSection,
                { it.label },
                viewModel::selectSection,
            )
        }
        Spacer(Modifier.height(d.spaceMicro))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = d.pageHorizontal),
        ) {
            StatisticsSectionPage(
                section = selectedSection,
                selection = selection,
                ready = ready,
                onGranularity = viewModel::selectGranularity,
                onStepGranularity = viewModel::stepGranularity,
                onShiftPeriod = viewModel::shiftPeriod,
                onOpenDate = { dateDialog = true },
                onOpenDetail = { id -> viewModel.openDetail(selectedSection, id) },
                onCloseDetail = { viewModel.closeDetail(selectedSection) },
                onShiftSleepMonth = viewModel::shiftSleepMonth,
                onShiftSleepTrendWindow = viewModel::shiftSleepTrendWindow,
                onSleepView = viewModel::selectSleepView,
                onSleepCalendarMode = viewModel::selectSleepCalendarMode,
                onSleepChartMode = viewModel::selectSleepChartMode,
                onStepSleepCalendarMode = viewModel::stepSleepCalendarMode,
                onStepSleepChartMode = viewModel::stepSleepChartMode,
                scrollState = when (selectedSection) {
                    StatisticsSection.CATEGORY -> categoryScroll
                    StatisticsSection.SLEEP -> null
                },
            )
        }
    }
    if (dateDialog) {
        AppCalendarDialog(
            selection.period.anchor,
            mode = when (selection.period.granularity) {
                StatisticsGranularity.DAY -> CalendarSelectionMode.DAY
                StatisticsGranularity.WEEK -> CalendarSelectionMode.WEEK
                StatisticsGranularity.MONTH -> CalendarSelectionMode.MONTH
                StatisticsGranularity.YEAR -> CalendarSelectionMode.YEAR
            },
            onDismiss = { dateDialog = false },
            onConfirm = {
                viewModel.selectDate(it)
                dateDialog = false
            },
        )
    }
}

@Composable
private fun StatisticsSectionPage(
    section: StatisticsSection,
    selection: StatisticsSelection,
    ready: StatisticsUiState.Ready,
    onGranularity: (StatisticsGranularity) -> Unit,
    onStepGranularity: (Int) -> Unit,
    onShiftPeriod: (Long) -> Unit,
    onOpenDate: () -> Unit,
    onOpenDetail: (Long?) -> Unit,
    onCloseDetail: () -> Unit,
    onShiftSleepMonth: (Long) -> Unit,
    onShiftSleepTrendWindow: (Int) -> Unit,
    onSleepView: (io.github.peanutpersimmon.rilo.presentation.statistics.SleepStatisticsView) -> Unit,
    onSleepCalendarMode: (io.github.peanutpersimmon.rilo.domain.service.SleepCalendarMode) -> Unit,
    onSleepChartMode: (io.github.peanutpersimmon.rilo.domain.service.SleepChartMode) -> Unit,
    onStepSleepCalendarMode: (Int) -> Unit,
    onStepSleepChartMode: (Int) -> Unit,
    scrollState: ScrollState?,
) {
    val d = AppTheme.dimens
    val navigationClearance = LocalFloatingNavigationClearance.current
    if (section == StatisticsSection.SLEEP) {
        SleepStatisticsScreen(
            ready.selection.sleepMonth,
            ready.sources.nightSleeps,
            ready.sources.naps,
            onShiftSleepMonth,
            ready.selection.sleepView,
            ready.selection.sleepCalendarMode,
            ready.selection.sleepChartMode,
            onSleepView,
            onSleepCalendarMode,
            onSleepChartMode,
            Modifier
                .fillMaxSize(),
            onStepSleepCalendarMode,
            onStepSleepChartMode,
            zoneId = ready.zoneId,
            trendWindow = ready.selection.sleepTrendWindow,
            onShiftTrendWindow = onShiftSleepTrendWindow,
        )
        return
    }

    val scroll = requireNotNull(scrollState)
    Column(
        Modifier
            .fillMaxSize()
            .discreteHorizontalSwipe { direction ->
                onStepGranularity(direction.step)
            }
    ) {
        StatisticsPeriodNavigation(
            selection.period,
            onGranularity,
            onShiftPeriod,
            onOpenDate,
        )
        val detail = selection.detailId(section)
        val names = ready.sources.labels.categoriesById.mapValues { it.value.name } + mapOf(-1L to "未分类")
        if (detail != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(d.minimumTouchTarget)
                        .clickable(onClick = onCloseDetail),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = AppTheme.type.sectionTitle)
                }
                Text(
                    names[detail] ?: "未分类",
                    Modifier.weight(1f),
                    style = AppTheme.type.secondary,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = maxOf(d.spaceL, navigationClearance)),
            verticalArrangement = Arrangement.spacedBy(d.spaceS),
        ) {
            if (detail == null) {
                StatisticsSummaryRow(formatHours(ready.sources.actual.totalMillis))
                CompactSectionSurface {
                    Text(
                        "分类时长",
                        style = AppTheme.type.compactSectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(d.spaceXs))
                    DurationRanking(
                        ready.sources.actual.rank(),
                        names,
                        onOpenDetail,
                        categoryColorKeys = ready.sources.labels.categoriesById.mapValues { it.value.colorKey },
                    )
                }
            } else {
                val selected = ready.sources.actual.selected(detail)
                StatisticsSummaryRow(
                    formatHours(selected.sumOf { segment -> segment.interval.durationMillis })
                )
                val period = selection.period
                if (period.granularity != StatisticsGranularity.DAY) {
                    CompactSectionSurface {
                        Text(
                            "趋势",
                            style = AppTheme.type.compactSectionTitle,
                        )
                        Spacer(Modifier.height(d.spaceXs))
                        DurationTrend(
                            ready.sources.actual.trend(selected),
                            period.granularity,
                        )
                    }
                }
                if (
                    period.granularity == StatisticsGranularity.MONTH ||
                    period.granularity == StatisticsGranularity.YEAR
                ) {
                    CompactSectionSurface {
                        Text(
                            "活跃分布",
                            style = AppTheme.type.compactSectionTitle,
                        )
                        Spacer(Modifier.height(d.spaceXs))
                        DurationHeatmap(
                            period.start.year,
                            period.start.monthValue.takeIf {
                                period.granularity == StatisticsGranularity.MONTH
                            },
                            ready.sources.actual.daily(selected),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsSummaryRow(value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "总实际时长",
            style = AppTheme.type.bodyCompact,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = AppTheme.type.bodyCompact,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

